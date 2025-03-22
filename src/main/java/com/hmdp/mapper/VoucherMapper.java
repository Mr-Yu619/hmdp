package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface VoucherMapper extends BaseMapper<Voucher> {
    //TODO
    // 这里的@Parm有什么用
    List<Voucher> queryVoucherofShop(@Param("shopId") Long shopId);
}
