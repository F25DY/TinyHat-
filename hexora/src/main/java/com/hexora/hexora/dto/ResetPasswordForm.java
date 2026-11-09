package com.hexora.hexora.dto;

import jakarta.validation.constraints.NotBlank;

public class ResetPasswordForm {

    @NotBlank(message = "La nueva contraseña es obligatoria")
    private String password;

    public ResetPasswordForm() {
    }

    public ResetPasswordForm(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

