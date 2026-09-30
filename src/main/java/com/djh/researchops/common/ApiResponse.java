package com.djh.researchops.common;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiResponse<T> {

    private int code;

    private String message;

    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> success(
            int code,
            String message,
            T data) {

        return new ApiResponse<>(code, message, data);
    }

    public static <T> ApiResponse<T> error(
            int code,
            String message) {

        return new ApiResponse<>(code, message, null);
    }
}