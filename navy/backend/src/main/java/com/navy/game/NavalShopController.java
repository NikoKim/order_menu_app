package com.navy.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navy.common.response.ApiResponse;
import com.navy.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/naval/shop")
public class NavalShopController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public record Purchase(String itemId,String requestId) {}
    public record Use(String requestId) {}
    public record Equipment(Long inventoryId,String requestId) {}
    private String key(){return "U:"+UserContext.get().userId();}
    private NavalShop shop(){return new NavalShop(jdbc,json);}
    @GetMapping public ApiResponse<?> profile(){return ApiResponse.success(shop().profile(key()));}
    @PostMapping("/purchases") public ApiResponse<?> purchase(@RequestBody Purchase request){return ApiResponse.success(shop().purchase(key(),request.itemId(),request.requestId()));}
    @PostMapping("/inventory/{id}/use") public ApiResponse<?> use(@PathVariable long id,@RequestBody Use request){return ApiResponse.success(shop().use(key(),id,request.requestId()));}
    @PostMapping("/equipment") public ApiResponse<?> equip(@RequestBody Equipment request){return ApiResponse.success(shop().equip(key(),request.inventoryId(),request.requestId()));}
}
