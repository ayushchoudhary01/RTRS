# 011 — BeanDefinitionOverrideException: @Configuration Class Name Collides With @Bean Method Name

## Symptom
```
BeanDefinitionOverrideException: Cannot register bean definition ... for bean
'eodSettlementJob' since there is already [Generic bean: class=...EodSettlementJob...] bound.
```

Two bean definitions fighting over the same name, even though there was only
one `@Bean` method actually intended to produce that bean.

## Root Cause
Spring registers every `@Configuration` class itself as a bean — this is how
Spring is able to proxy the class and intercept calls between `@Bean` methods
within it (so that calling one `@Bean` method from another returns the
existing singleton rather than creating a new instance).

The default bean name for a class is its simple class name with the first
letter lowercased. A class named `EodSettlementJob` therefore registers itself
as a bean named `eodSettlementJob`.

If a `@Bean` method *inside that same class* is also named `eodSettlementJob()`,
it produces a second bean definition under the identical name — the
`@Configuration` class's own auto-registered bean, and the method's bean,
collide.

```java
@Configuration                    // auto-registers as bean "eodSettlementJob"
public class EodSettlementJob {

    @Bean
    public Job eodSettlementJob(Step settlementStep) {  // ALSO registers as "eodSettlementJob"
        ...
    }
}
```

## Fix
Rename the `@Bean` method so it no longer matches the enclosing class's
default bean name. The Spring Batch `Job`'s own internal name (the string
passed to `JobBuilder`) is unrelated and does not need to change:

```java
@Bean
public Job eodSettlementJobBean(Step settlementStep) {       // renamed method
    return new JobBuilder("eodSettlementJob", jobRepository)  // internal job name unchanged
            .start(settlementStep)
            .build();
}
```

Update any field/parameter injecting this bean (`Job eodSettlementJobBean`)
to match the new method name.

## Lesson
Avoid naming a `@Bean` method exactly the same as its enclosing `@Configuration`
class (modulo the first-letter-lowercase convention). This is most likely to
happen when a configuration class is named after the single artifact it
produces (e.g. a `Job`, a `DataSource`, a `RestTemplate`) — a natural and
common naming choice that silently sets up this exact collision.