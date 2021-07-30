[![](https://jitpack.io/v/cojen/Maker.svg)](https://jitpack.io/#cojen/Maker)

The Cojen/Maker module is a low-level dynamic Java class generator which is designed for [ease of use](https://github.com/cojen/Maker/wiki/Ease-of-use).

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
- [Coding patterns](docs/CodingPatterns.md)
- [Examples](example/main/java/org/cojen/example)

A key feature of this module is that the JVM operand stack isn't directly accessible. Local variables are used exclusively, and conversion to the stack-based representation is automatic. In some cases this can result in sub-optimal bytecode, but this only affects performance when the code is interpreted. Modern JVMs perform liveness analysis when generating machine code, and this eliminates the need to carefully utilize the operand stack.

In addition to simplifying basic class generation, the features of the `java.lang.invoke` package are fully integrated, and without all the complexity. The `ObjectMethods` example shows how to define a bootstrap method which generates code "just in time".

Design features
---------------

The API is designed to be very simple, defined by only eight interfaces and no public classes. Despite its tiny size, the API supports nearly all of the JVM features and bytecode instructions. Features like generics aren't currently supported, although dynamically generated classes don't require them due to type erasure. Instructions which directly manipulate stack operands aren't supported either, because such access isn't necessary.

In order to keep the API simple, some capabilities are merged into common interfaces. In particular, there's no `Type` class to represent things like variable types. Instead, a plain `Class` can be used to specify a type, as can a string name or descriptor. To reference types which are currently being made, a `ClassMaker` instance can be used as a type. `Variable` instances can be used as generic type specifiers, and this doesn't necessarily create an actual variable in the generated bytecode.

Java bytecode is imperative in nature, and for this reason, the API is also imperative. Code execution order is top to bottom, labels are used to control execution flow, and goto instructions are supported. High-level scoping features are provided in a few cases as a convenience. In particular, exception handlers can be written with a callback, eliminating the need to specify an explicit goto to skip past the handler. Writing a `finally` clause is greatly simplified, ensuring that all exit paths from a guarded scope are properly covered.

Another nice feature is that type conversions between primitive types is performed automatically. Widening conversions are performed automatically, as are boxing/unboxing conversions. Unsafe narrowing type conversions aren't performed automatically, and the rules are stricter than what the Java language permits. Narrowing conversions are performed when given a constant, when it can be proven that narrowing causes no loss of information.

Finished classes can be loaded immediately, or they can be written out to a file. Classes which are immediately loaded are eligible to be unloaded when all generated classes in the group are no longer referenced. In this context, a "group" is defined by a parent `ClassLoader` and an optional key object. The group itself is a child `ClassLoader`, and so classes in the group have package-level access to each other. Applications can control permissions and class unloading by carefully choosing an appropriate key object. Classes can also be defined as "hidden", in which case they can be unloaded even when the group itself cannot be unloaded.

The `java.lang.invoke` package provides powerful features for dynamically generating code, but it's quite complicated and somewhat incomplete. Nonetheless, it does provide very useful features for supporting the so called `invokedynamic` instruction. The Cojen/Maker API fully supports these features, and it does so seamlessly. `MethodHandle` and `VarHandle` instances can be freely exchanged at various points in the API, at code generation time or at runtime. To use the `invokedynamic` instruction, the API provides `indy` and `condy` methods for specifying the required bootstrap method. Any kind of constant can be passed into the bootstrap method, because any special encoding strategies are performed automatically, including the handling of `Constable` objects.

Another nice feature is the `setExact` method, which allows arbitrary object instances to be passed into dynamically generated classes. Ordinarily, only simple constants can be specified, or else the "condy" feature must be used to reconstruct the object upon demand. Underneath the covers, `setExact` uses the condy feature to extract the object instance from a special hash table, keyed by the class instance itself. It doesn't work when generated classes are loaded from a file.

The implementation of the Cojen/Maker system is tiny, and it has no dependencies. The total size is about 7300 lines of code, where a line of code is defined as a non-comment line which contains more than one non-whitespace character. For comparison, ASM and Byte Buddy have about 25,000 and 130,000 lines of code respectively, although in fairness, these frameworks do have more features.
