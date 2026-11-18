package com.kanbanlive.boards.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CreateBoardRequest(@NotBlank @Size(max = 120) String name) {
}
