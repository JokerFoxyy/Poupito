package com.poupito.api.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Política de senha forte compartilhada entre cadastro e redefinição de senha:
 * ao menos 12 caracteres, com maiúscula, minúscula, número e símbolo.
 *
 * <p>Não é aplicada no login — lá a senha só precisa não ser vazia, senão usuários
 * cadastrados sob a política antiga (≥10 com letra e número) ficariam trancados fora.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ FIELD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT })
@Retention(RUNTIME)
public @interface StrongPassword {

	String message() default "A senha deve ter ao menos 12 caracteres, incluindo maiúscula, "
			+ "minúscula, número e símbolo";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
