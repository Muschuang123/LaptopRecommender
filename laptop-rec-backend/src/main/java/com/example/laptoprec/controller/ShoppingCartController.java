package com.example.laptoprec.controller;

import com.example.laptoprec.common.Result;
import com.example.laptoprec.dto.AddCartItemRequest;
import com.example.laptoprec.dto.DeleteCartItemsRequest;
import com.example.laptoprec.service.ShoppingCartService;
import com.example.laptoprec.vo.LaptopListItemVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class ShoppingCartController {
    private final ShoppingCartService shoppingCartService;

    public ShoppingCartController(ShoppingCartService shoppingCartService) {
        this.shoppingCartService = shoppingCartService;
    }

    @GetMapping
    public Result<List<LaptopListItemVO>> list() {
        return Result.ok(shoppingCartService.getItems());
    }

    @PostMapping("/items")
    public Result<Void> add(@Valid @RequestBody AddCartItemRequest request) {
        shoppingCartService.addItem(request.getLaptopId());
        return Result.ok(null);
    }

    @DeleteMapping("/items")
    public Result<Void> delete(@Valid @RequestBody DeleteCartItemsRequest request) {
        shoppingCartService.deleteItems(request.getLaptopIds());
        return Result.ok(null);
    }

    @DeleteMapping
    public Result<Void> clear() {
        shoppingCartService.clear();
        return Result.ok(null);
    }
}
