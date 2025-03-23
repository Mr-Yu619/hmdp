package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Autowired
    private IFollowService followService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result queryById(Integer id) {
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("博客不存在或已经被删除");
        }
        // 查询博客的作者信息并更新，以及当前用户是否喜欢博客
        queryBlogUser(blog);
        queryBlogLikes(id);
        return Result.ok(blog);
    }

    // 查询点赞高的博客，排序好，每一个博客都需要写好那新加的三项
    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        records.forEach( blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        // 1. 获取当前用户信息
        Long userId = UserHolder.getUser().getId();
        // 2. 如果当前用户未点赞，则点赞数+1，同时将用户加入到Zset集合
        String key = BLOG_LIKED_KEY + id;
        // 尝试获取score
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        // 为null，代表没点赞过
        if(score == null){
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            if(success){
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }else {
            // 点过赞了，移除
            boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
            if(success){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    // 查询博客的最开始的前5个喜欢
    @Override
    public Result queryBlogLikes(Integer id) {
        String key = BLOG_LIKED_KEY + id;
        // zrange key 0 4 查询zset中前5个元素
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        // 如果是空的（可能没人点赞），直接返回一个空集合
        if(top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //使用StrUtil.join(",", ids)将ID列表拼接成逗号分隔的字符串，用于构建SQL的ORDER BY FIELD子句。
        String idsStr = StrUtil.join(",", ids);
        // select * from tb_user where id in (ids[0], ids[1], ...) order by field(id, id[0], id[1] ...)
        List<UserDTO> userDTOS = userService.query().in("id", ids)
                .last("order by field(id," + idsStr + ")")
                .list().stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    // 保存博客，并且把博客id推送给粉丝
    @Override
    public Result saveBlog(Blog blog) {
        // 获取当前登录用户
        UserDTO userDTO = UserHolder.getUser();
        blog.setUserId(userDTO.getId());
        // 保存博文
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("新增笔记失败");
        }
        // 如果保存成功，则获取保存笔记的发布者id，用该id去follow_user表中查对应的粉丝id，推送
        List<Follow> follows = followService.query().eq("follow_user_id", userDTO.getId()).list();
        for (Follow follow : follows) {
            Long userId = follow.getUserId();
            // 相当于一个邮箱，每个用户都有
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key,  blog.getId().toString(), System.currentTimeMillis());
        }

        return Result.ok(blog.getId());
    }

    // 查询是否有关注的博主发送了博客
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = FEED_KEY + userId;
        // 2.查询set中的对
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key,0, max, offset, 2);
        // 3.非空判断
        if (typedTuples == null || typedTuples.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        // 4.解析数据，blogId, minTime offset 这里指定创建的list大小，可以略微提高效率
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String id = typedTuple.getValue();
            ids.add(Long.valueOf(id));
            long time = typedTuple.getScore().longValue();
            if(time == minTime){
                os++;
            }else {
                minTime = time;
                os = 1;
            }
        }

        String idsStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("ids", ids).last("ORDER BY FIELD(id" + idsStr + ")").list();

        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }

        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    // 查询当前用户是否喜欢了该博客
    private void isBlogLiked(Blog blog){
        // 1.获取当前用户信息
        UserDTO userDTO = UserHolder.getUser();
        // 如果用户未登陆，直接return结束逻辑
        if(userDTO == null){
            return;
        }
        // 2.判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        // 获取到在这个Zset中当前用户的值，应该是个时间戳
        Double score = stringRedisTemplate.opsForZSet().score(key, userDTO.getId().toString());
        // isLike这个东西不存在于Blog表中，只是一个为每一个用户特有的
        blog.setIsLike(score != null);
    }

    // 查询博客的作者,并据此来更新博客的基本信息，如用户昵称，用户图标，这些都是后加的，要在博客上显示的
    private void queryBlogUser(Blog blog){
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

}
