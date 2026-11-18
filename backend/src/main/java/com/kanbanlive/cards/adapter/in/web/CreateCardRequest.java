package com.kanbanlive.cards.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CreateCardRequest(@NotBlank @Size(max = 255) String title, @Size(max = 10000) String description) {
}
