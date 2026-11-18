package com.kanbanlive.sharedkernel.application;

/** Contrato minimo opcional. Se prefieren interfaces especificas por caso de uso. */
public interface UseCase<I, O> {

    O execute(I input);
}
