package com.kanbanlive.boards.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record RenameBoardRequest(@NotBlank @Size(max = 120) String name, @NotNull Long version) {
}
