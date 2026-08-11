package com.seckill.controller;

import com.seckill.entity.SeckillGoods;
import com.seckill.entity.SeckillOrder;
import com.seckill.service.SeckillOrderService;
import com.seckill.service.SeckillService;
import com.seckill.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Thymeleaf 页面控制器
 * 处理所有前端页面路由
 */
@Controller
public class PageController {

    @Autowired
    private SeckillService seckillService;
    @Autowired
    private SeckillOrderService seckillOrderService;

    /**
     * 首页 - 商品列表页
     */
    @GetMapping({"/", "/goods/list"})
    public String goodsList(Model model) {
        List<SeckillGoods> goodsList = seckillService.listGoods();
        model.addAttribute("goodsList", goodsList);
        model.addAttribute("currentUserId", UserContext.getCurrentUserId());
        return "goods-list";
    }

    /**
     * 秒杀商品详情页
     */
    @GetMapping("/goods/{id}")
    public String goodsDetail(@PathVariable Long id, Model model) {
        SeckillGoods goods = seckillService.getGoodsById(id);
        if (goods == null) {
            return "error/404";
        }
        model.addAttribute("goods", goods);
        model.addAttribute("currentUserId", UserContext.getCurrentUserId());
        return "seckill-detail";
    }

    /**
     * 秒杀结果页
     * 支持通过订单号或用户ID查询
     */
    @GetMapping("/order/result")
    public String seckillResult(@RequestParam(required = false) String orderNo,
                                @RequestParam(required = false) Long goodsId,
                                Model model) {
        // Long userId = UserContext.getCurrentUserId();
        // model.addAttribute("orderNo", orderNo);
        // model.addAttribute("userId", userId);
        // model.addAttribute("goodsId", goodsId);
        SeckillOrder order = seckillOrderService.getOrderDetail(orderNo, goodsId);
        model.addAttribute("order", order);
        if (order == null) {
            return "error/404";
        }
        return "seckill-result";
    }
}