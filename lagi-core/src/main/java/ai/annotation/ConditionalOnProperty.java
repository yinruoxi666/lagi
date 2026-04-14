package ai.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConditionalOnProperty {


    String[] name() default {};


    String[] value() default {};


    String prefix() default "";


    String havingValue() default "";


    boolean matchIfMissing() default false;
}
