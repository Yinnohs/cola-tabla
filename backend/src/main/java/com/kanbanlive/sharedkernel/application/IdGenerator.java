package com.kanbanlive.sharedkernel.application;

import java.util.UUID;

/** Puerto de generacion de identificadores. */
public interface IdGenerator {

    UUID newId();
}
