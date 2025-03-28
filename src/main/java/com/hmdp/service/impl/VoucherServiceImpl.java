package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;



    @Override
    public Result queryVoucherOfShop(Long shopId) {
        //getBaseMapper 是ServiceImpl类中的方法，返回的是VoucherMapper对象，通过Bean管理的
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        return Result.ok(vouchers);
    }

    //这里添加优惠券的逻辑不是简单的添加，需要同步更新优惠券秒杀信息表
    //TODO 现在的问题是为什么Voucher表中可以直接拿到库存开始时间，结束时间，什么时候更新的，难道传进来就是这样的么
    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        //添加优惠券后立马更新秒杀信息
        save(voucher);//IVoucherService，实际上是IService提供的
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);

    }
}
