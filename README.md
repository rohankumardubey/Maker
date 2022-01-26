[![](https://jitpack.io/v/cojen/Maker.svg)](https://jitpack.io/#cojen/Maker)

The Cojen/Maker framework is a lightweight, full-featured, low-level dynamic Java class generator which is designed for [ease of use](https://github.com/cojen/Maker/wiki/Ease-of-use).

Here's a simple "hello, world" example:

```java
ClassMaker cm = ClassMaker.begin().public_();

// public static void run()...
MethodMaker mm = cm.addMethod(null, "run").public_().static_();

// System.out.println(...
mm.var(System.class).field("out").invoke("println", "hello, world");

Class<?> clazz = cm.finish();
clazz.getMethod("run").invoke(null);
```

- [Javadocs](https://cojen.github.io/Maker/javadoc/org.cojen.maker/org/cojen/maker/package-summary.html)
- [Coding patterns](https://github.com/cojen/Maker/wiki/Coding-patterns)
- [Examples](example/main/java/org/cojen/example)

A key feature of the framework is that the JVM operand stack isn't directly accessible. Local variables are used exclusively, and conversion to the stack-based representation is automatic. In some cases this can result in sub-optimal bytecode, but this only affects performance when the code is interpreted. Modern JVMs perform liveness analysis when generating machine code, and this eliminates the need to carefully utilize the operand stack.

In addition to simplifying basic class generation, the features of the `java.lang.invoke` package are fully integrated, but without all the complexity. The `ObjectMethods` example shows how to define a bootstrap method which generates code "just in time".

Design features
---------------

The API is designed to be very simple, defined by a few interfaces and no public classes. Despite its tiny size, the API supports nearly all of the JVM features and bytecode instructions. Features like generics aren't currently supported, although due to type erasure, dynamically generated classes don't require generics anyhow. Instructions which directly manipulate stack operands aren't supported either, because such access isn't necessary with this API.

In order to keep the API simple, some capabilities are merged into common interfaces. In particular, there's no `Type` class to represent things like variable types. Instead, a plain `Class` can be used to specify a type, as can a string name or descriptor. To reference types which are currently being made, a `ClassMaker` instance can be used as a type. `Variable` instances themselves can be used as generic type specifiers, and this doesn't necessarily create an actual variable in the generated bytecode.

Java bytecode is imperative in nature, and for this reason, the API is also imperative. Code execution order is top to bottom, and labels are used to control execution flow. Higher-level scoping features are provided in a few cases as a convenience. In particular, exception handlers can be generated with a callback, eliminating the need to specify an explicit goto to skip past the handler. This is even more helpful when generating `finally` clauses, as it ensures that all exit paths from a guarded scope are properly covered.

Type conversions between primitive types are performed automatically, including boxing/unboxing conversions. Narrowing conversions aren't performed automatically, unless it can be proven that this causes no loss of information. This effectively limits such conversions to constants only.

Finished classes can be loaded immediately, or they can be written out to a file. Classes which are immediately loaded are eligible to be unloaded when all generated classes in the group are no longer referenced. In this context, a "group" is defined by a parent `ClassLoader` and an optional key object. The group itself is a child `ClassLoader`, and so classes in the group have package-level access to each other. Applications can control permissions and unloading behavior by carefully choosing an appropriate key object. Classes can also be defined as "hidden", in which case they can be unloaded even when the group itself cannot be unloaded.

The `java.lang.invoke` package provides powerful features for dynamically generating code, but it's quite complicated and somewhat incomplete. Nonetheless, it does provide very useful features for supporting the so called `invokedynamic` instruction. The Cojen/Maker API fully supports these features, and it does so seamlessly. `MethodHandle` and `VarHandle` instances can be freely exchanged at various points in the API, at code generation time or at runtime. To use the `invokedynamic` instruction, the API provides `indy` and `condy` methods for specifying the required bootstrap method. Any kind of constant can be passed into the bootstrap method, because special encoding strategies are performed automatically, including the handling of `Constable` objects.

Another nice feature is the `setExact` method, which allows arbitrary object instances to be passed into dynamically generated classes. Ordinarily, only simple constants can be specified, or else the "condy" feature must be used to reconstruct the object upon demand. Underneath the covers, `setExact` uses the condy feature to extract the object instance from a special hash table, keyed by the class instance itself. This feature doesn't work for generated classes which are loaded from a file.

The implementation of the Cojen/Maker system is tiny, and it has no dependencies. The total size is about 7400 lines of code, where a line of code is defined as a non-comment line which contains more than one non-whitespace character. For comparison, ASM and Byte Buddy have about 25,000 and 130,000 lines of code respectively, although in fairness, these frameworks do have more features.

Limitations
-----------

The Cojen/Maker framework is designed for implementing dynamic languages, and for designing utilities that achieve higher performance than is possible when using the reflection API. It isn't designed for modifying classes or for implementing instrumentation agents. That is, you cannot start with an existing class and make modifications to it &mdash; classes are only ever made "from scratch". A future version might support class modifications, but there's no plans at this time.

Although the framework can be used for writing a frontend compiler, it doesn't have any facilities for reading class symbols. For example, it's possible to write a Java compiler that uses Cojen for writing class files, but it would need another tool for extracting symbols from pre-compiled jar files and so forth. Such a feature could be added of course, but it's a lower priority.

Because of its somewhat low-level design, the framework doesn't prevent the creation of broken classes. For example, failing to definitely assign a value to a variable will cause a `VerifyError` to be thrown when loading the class. The [Coding errors](https://github.com/cojen/Maker/wiki/Coding-errors) page has more details.
