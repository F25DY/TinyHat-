package com.hexora.hexora.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class InvitacionRequest {

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato de correo no es válido")
    @Size(max = 254, message = "El correo no puede exceder 254 caracteres")
    private String email;

    public InvitacionRequest() {
    }

    public InvitacionRequest(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

