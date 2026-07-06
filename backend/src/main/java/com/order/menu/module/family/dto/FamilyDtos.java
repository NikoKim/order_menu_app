package com.order.menu.module.family.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public final class FamilyDtos {
    private FamilyDtos() {
    }

    public record FamilyResponse(Long shopId, String shopName, String familyName, String familyCode,
                                 String familyImageUrl, String joinMode, String memberStatus) {
    }

    public record JoinFamilyRequest(
            @NotBlank(message = "家庭号不能为空")
            @Pattern(regexp = "[0-9A-Z]{6}", message = "家庭号必须为6位大写字母或数字") String familyCode
    ) {
    }

    public record MyFamiliesResponse(List<FamilyResponse> families) {
    }

    public record FamilyMemberResponse(Long id, Long userId, String nickname, String phone, String avatarUrl,
                                       String status) {
    }
}
