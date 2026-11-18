package com.kanbanlive.lists.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CreateListRequest(@NotBlank @Size(max = 120) String name) {
}
