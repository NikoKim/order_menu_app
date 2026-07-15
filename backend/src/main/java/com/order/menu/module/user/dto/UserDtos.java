package com.order.menu.module.user.dto;

import jakarta.validation.constraints.Size;

public final class UserDtos {
    private UserDtos() {
    }

    public record UpdateMeRequest(
            @Size(max = 50, message = "Nickname must not exceed 50 characters") String nickname,
            @Size(max = 30, message = "Phone must not exceed 30 characters") String phone,
            @Size(max = 255, message = "Avatar URL must not exceed 255 characters") String avatarUrl,
            Boolean allowPrivateChat,
            Boolean autoJoinFamilyChat,
            Boolean showJoinedFamilies,
            Boolean showPhone
    ) {
        public UpdateMeRequest(String nickname, String phone, String avatarUrl,
                               Boolean allowPrivateChat, Boolean autoJoinFamilyChat) {
            this(nickname, phone, avatarUrl, allowPrivateChat, autoJoinFamilyChat, null, null);
        }
    }

    public record CurrentUserResponse(
            Long id,
            String nickname,
            String role,
            String avatarUrl,
            String phone,
            Boolean allowPrivateChat,
            Boolean autoJoinFamilyChat,
            Boolean showJoinedFamilies,
            Boolean showPhone
    ) {
    }
}
