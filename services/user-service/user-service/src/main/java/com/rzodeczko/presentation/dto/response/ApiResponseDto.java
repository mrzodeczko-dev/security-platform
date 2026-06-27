package com.rzodeczko.presentation.dto.response;

public record ApiResponseDto<T>(T data, String error) {
    public static <T> ApiResponseDto<T> data(T data) {
        return new ApiResponseDto<T>(data, null);
    }

    public static ApiResponseDto<Void> error(String error) {
        return new ApiResponseDto<>(null, error);
    }
}
