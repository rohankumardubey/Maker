/*
 *  Copyright (C) 2019 Cojen.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.maker;

import java.io.IOException;
import java.io.OutputStream;

import java.lang.invoke.MethodHandles;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.util.concurrent.ThreadLocalRandom;

import static java.util.Objects.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
final class TheClassMaker extends Attributed implements ClassMaker, Typed {
    static final boolean DEBUG = Boolean.getBoolean(ClassMaker.class.getName() + ".DEBUG");

    private static volatile Method cDefineHidden;
    private static Object cHiddenClassOptions;
    private static Object cUnsafe;

    private static Method defineHidden() {
        Method m = cDefineHidden;

        if (m != null) {
            return m;
        }

        try {
            var optionClass = Class.forName("java.lang.invoke.MethodHandles$Lookup$ClassOption");
            Object options = Array.newInstance(optionClass, 1);
            Array.set(options, 0, optionClass.getField("NESTMATE").get(null));
            m = MethodHandles.Lookup.class.getMethod
                ("defineHiddenClass", byte[].class, boolean.class, options.getClass());
            cHiddenClassOptions = options;
            cDefineHidden = m;
            return m;
        } catch (Throwable e) {
        }

        try {
            var unsafeClass = Class.forName("sun.misc.Unsafe");
            m = unsafeClass.getMethod
                ("defineAnonymousClass", Class.class, byte[].class, Object[].class);
            var field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            cUnsafe = field.get(null);
            cDefineHidden = m;
            return m;
        } catch (Throwable e) {
        }

        throw new UnsupportedOperationException("Cannot define hidden classes");
    }

    private final TheClassMaker mParent;
    private final boolean mExternal;
    private final MethodHandles.Lookup mLookup;
    private final ClassInjector mInjector;
    private final ClassInjector.Group mInjectorGroup;

    final ConstantPool.C_Class mThisClass;

    private ConstantPool.C_Class mSuperClass;

    // Stashed by Type.begin to prevent GC of this type being defined.
    Map mTypeCache;

    int mModifiers;

    private Set<ConstantPool.C_Class> mInterfaces;
    private Map<String, TheFieldMaker> mFields;
    private List<TheMethodMaker> mMethods;

    private boolean mHasConstructor;

    private ArrayList<TheMethodMaker> mClinitMethods;

    private Attribute.BootstrapMethods mBootstrapMethods;

    private Attribute.NestMembers mNestMembers;

    private Attribute.InnerClasses mInnerClasses;

    // Maps constants to static final fields. Accessed by TheMethodMaker.
    Map<ConstantPool.Constant, ConstantPool.C_Field> mResolvedConstants;

    // -1: finished, 0: not finished, 1: has exact constants
    private int mFinished;

    static TheClassMaker begin(boolean external, String className, boolean explicit,
                               ClassLoader parentLoader, Object key, MethodHandles.Lookup lookup)
    {
        if (parentLoader == null) {
            parentLoader = ClassLoader.getSystemClassLoader();
        }

        ClassInjector injector = ClassInjector.lookup(explicit, parentLoader, key);

        return new TheClassMaker(null, external, className, lookup, injector);
    }

    private TheClassMaker(TheClassMaker parent, boolean external,
                          String className, MethodHandles.Lookup lookup, ClassInjector injector)
    {
        super(new ConstantPool());

        mParent = parent;
        mExternal = external;
        mLookup = lookup;
        mInjector = injector;

        if (injector.isExplicit()) {
            Objects.requireNonNull(className);
        } else {
            // Only check the parent loader when it will be used directly. This avoids creating
            // useless class loading lock objects that never get cleaned up.
            className = injector.reserve(className, lookup != null);
        }

        // Maintain a strong reference to the group.
        mInjectorGroup = lookup != null ? null : injector.groupForClass(className);

        mThisClass = mConstants.addClass(Type.begin(injector, this, className));
    }

    private TheClassMaker(TheClassMaker from, String className) {
        this(from, from.mExternal, className, from.mLookup, from.mInjector);
    }

    @Override
    public ClassMaker another(String className) {
        return new TheClassMaker(this, className);
    }

    @Override
    public ClassMaker public_() {
        checkFinished();
        mModifiers = Modifiers.toPublic(mModifiers);
        return this;
    }

    @Override
    public ClassMaker private_() {
        checkFinished();
        mModifiers = Modifiers.toPrivate(mModifiers);
        return this;
    }

    @Override
    public ClassMaker protected_() {
        checkFinished();
        mModifiers = Modifiers.toProtected(mModifiers);
        return this;
    }

    @Override
    public ClassMaker static_() {
        checkFinished();
        mModifiers = Modifiers.toStatic(mModifiers);
        return this;
    }

    @Override
    public ClassMaker final_() {
        checkFinished();
        mModifiers = Modifiers.toFinal(mModifiers);
        return this;
    }

    @Override
    public ClassMaker interface_() {
        checkFinished();
        mModifiers = Modifiers.toInterface(mModifiers);
        type().toInterface();
        return this;
    }

    @Override
    public ClassMaker abstract_() {
        checkFinished();
        mModifiers = Modifiers.toAbstract(mModifiers);
        return this;
    }

    @Override
    public ClassMaker synthetic() {
        checkFinished();
        mModifiers = Modifiers.toSynthetic(mModifiers);
        return this;
    }

    @Override
    public ClassMaker extend(Object superClass) {
        requireNonNull(superClass);
        if (mSuperClass != null) {
            throw new IllegalStateException("Super class has already been assigned");
        }
        doExtend(superClass);
        return this;
    }

    private void doExtend(Object superClass) {
        mSuperClass = mConstants.addClass(typeFrom(superClass));
        type().resetInherited();
    }

    ConstantPool.C_Class superClass() {
        ConstantPool.C_Class superClass = mSuperClass;
        if (superClass == null) {
            doExtend(Object.class);
            superClass = mSuperClass;
        }
        return superClass;
    }

    Type superType() {
        return superClass().mType;
    }

    @Override
    public ClassMaker implement(Object iface) {
        requireNonNull(iface);
        checkFinished();
        if (mInterfaces == null) {
            mInterfaces = new LinkedHashSet<>(4);
        }
        mInterfaces.add(mConstants.addClass(typeFrom(iface)));
        type().resetInherited();
        return this;
    }

    /**
     * @return empty set if no interfaces
     */
    Set<Type> allInterfaces() {
        if (mInterfaces == null) {
            return Collections.emptySet();
        }

        Set<Type> all = new LinkedHashSet<>(mInterfaces.size());

        for (ConstantPool.C_Class clazz : mInterfaces) {
            Type type = clazz.mType;
            all.add(type);
            all.addAll(type.interfaces());
        }

        return all;
    }

    @Override
    public TheFieldMaker addField(Object type, String name) {
        requireNonNull(type);
        requireNonNull(name);

        checkFinished();

        if (mFields == null) {
            mFields = new LinkedHashMap<>();
        } else if (mFields.containsKey(name)) {
            throw new IllegalStateException("Field is already defined: " + name);
        }

        Type tType = typeFrom(type);

        var fm = new TheFieldMaker(this, type().defineField(false, tType, name));
        mFields.put(name, fm);

        return fm;
    }

    TheFieldMaker addSyntheticField(Type type, String prefix) {
        checkFinished();

        String name;
        if (mFields == null) {
            mFields = new LinkedHashMap<>();
            name = prefix + '0';
        } else {
            name = prefix + mFields.size();
            while (mFields.containsKey(name)) {
                name = prefix + ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
            }
        }

        var fm = new TheFieldMaker(this, type().defineField(false, type, name));
        fm.synthetic();
        mFields.put(name, fm);

        return fm;
    }
 
    @Override
    public TheMethodMaker addMethod(Object retType, String name, Object... paramTypes) {
        if (name.equals("<clinit>")) {
            throw new IllegalArgumentException("Use the addClinit method");
        }
        if (name.equals("<init>")) {
            throw new IllegalArgumentException("Use the addConstructor method");
        }
        checkFinished();
        return doAddMethod(retType, name, paramTypes);
    }

    @Override
    public TheMethodMaker addConstructor(Object... paramTypes) {
        checkFinished();
        return doAddMethod(null, "<init>", paramTypes);
    }

    private TheMethodMaker doAddMethod(Object retType, String name, Object... paramTypes) {
        var mm = new TheMethodMaker(this, defineMethod(retType, name, paramTypes));
        doAddMethod(mm);

        if (!mHasConstructor && name.equals("<init>")) {
            mHasConstructor = true;
        }

        return mm;
    }

    void doAddMethod(TheMethodMaker mm) {
        if (mMethods == null) {
            mMethods = new ArrayList<>();
        }
        mMethods.add(mm);
    }

    Type.Method defineMethod(Object retType, String name, Object... paramTypes) {
        Type tRetType = retType == null ? Type.VOID : typeFrom(retType);

        Type[] tParamTypes;
        if (paramTypes == null) {
            tParamTypes = new Type[0];
        } else {
            tParamTypes = new Type[paramTypes.length];
            for (int i=0; i<paramTypes.length; i++) {
                tParamTypes[i] = typeFrom(paramTypes[i]);
            }
        }

        return type().defineMethod(false, tRetType, name, tParamTypes);
    }

    @Override
    public TheMethodMaker addClinit() {
        checkFinished();

        TheMethodMaker mm;
        if (mClinitMethods == null) {
            mClinitMethods = new ArrayList<>();
            mm = doAddMethod(null, "<clinit>");
        } else {
            mm = new TheMethodMaker(mClinitMethods.get(mClinitMethods.size() - 1));
        }

        mm.static_();
        mm.useReturnLabel();
        mClinitMethods.add(mm);
        return mm;
    }

    @Override
    public TheClassMaker addInnerClass(String className) {
        return addInnerClass(className, null);
    }

    TheClassMaker addInnerClass(final String className, final Type.Method hostMethod) {
        TheClassMaker nestHost = nestHost(this);

        String prefix = name();
        int ix = prefix.lastIndexOf('-');
        if (ix > 0) {
            prefix = prefix.substring(0, ix);
        }

        var innerClasses = innerClasses();

        String fullName;

        if (className == null) {
            fullName = prefix + '$' + innerClasses.classNumberFor("");
        } else {
            if (className.indexOf('.') >= 0) {
                throw new IllegalArgumentException("Not a simple name: " + className);
            }
            if (hostMethod == null ||
                ((ix = prefix.indexOf('$')) >= 0 && ++ix < prefix.length()
                 && !Character.isJavaIdentifierStart(prefix.charAt(ix))))
            {
                fullName = prefix + '$' + className;
            } else {
                fullName = prefix + '$' + innerClasses.classNumberFor(className) + className;
            }
        }

        var clazz = new TheClassMaker(this, fullName);
        clazz.setNestHost(nestHost.type());
        nestHost.addNestMember(clazz.type());

        if (hostMethod != null) {
            clazz.setEnclosingMethod(type(), hostMethod);
        }

        innerClasses.add(clazz, this, className);
        clazz.innerClasses().add(clazz, this, className);

        return clazz;
    }

    private static TheClassMaker nestHost(TheClassMaker cm) {
        while (true) {
            cm.checkFinished();
            TheClassMaker parent = cm.mParent;
            if (parent == null) {
                return cm;
            }
            cm = parent;
        }
    }

    private void setNestHost(Type nestHost) {
        addAttribute(new Attribute.NestHost(mConstants, mConstants.addClass(nestHost)));
    }

    private synchronized void addNestMember(Type nestMember) {
        if (mNestMembers == null) {
            mNestMembers = new Attribute.NestMembers(mConstants);
            addAttribute(mNestMembers);
        }
        mNestMembers.add(mConstants.addClass(nestMember));
    }

    private void setEnclosingMethod(Type hostType, Type.Method hostMethod) {
        addAttribute(new Attribute.EnclosingMethod
                     (mConstants, mConstants.addClass(hostType),
                      mConstants.addNameAndType(hostMethod.name(), hostMethod.descriptor())));
    }

    private Attribute.InnerClasses innerClasses() {
        if (mInnerClasses == null) {
            mInnerClasses = new Attribute.InnerClasses(mConstants);
            addAttribute(mInnerClasses);
        }
        return mInnerClasses;
    }

    @Override
    public AnnotationMaker addAnnotation(Object annotationType, boolean visible) {
        return addAnnotation(new TheAnnotationMaker(this, annotationType), visible);
    }

    @Override
    public ClassMaker sourceFile(String fileName) {
        checkFinished();
        addAttribute(new Attribute.SourceFile(mConstants, fileName));
        return this;
    }

    @Override
    public Object arrayType(int dimensions) {
        if (dimensions < 1 || dimensions > 255) {
            throw new IllegalArgumentException();
        }

        Type type = type();
        do {
            type = type.asArray();
        } while (--dimensions > 0);

        final Type fType = type;

        return (Typed) () -> fType;
    }

    @Override
    public ClassLoader classLoader() {
        return mLookup != null ? mLookup.lookupClass().getClassLoader() : mInjectorGroup;
    }

    String name() {
        return type().name();
    }

    @Override
    public Class<?> finish() {
        boolean hasExactConstants = mFinished == 1;
        String name = name();

        Class clazz;
        try {
            if (mLookup == null) {
                clazz = mInjector.define(mInjectorGroup, name, finishBytes(false));
            } else {
                try {
                    clazz = mLookup.defineClass(finishBytes(false));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        } finally {
            Type.uncache(mTypeCache, name);
        }

        if (hasExactConstants) {
            ConstantsRegistry.finish(this, clazz);
        }

        return clazz;
    }

    @Override
    public MethodHandles.Lookup finishHidden() {
        if (mLookup == null) {
            throw new IllegalStateException("No lookup was provided to the begin method");
        }

        Method m = defineHidden();
        Object options = cHiddenClassOptions;

        boolean hasExactConstants = mFinished == 1;
        String originalName = name();

        byte[] bytes = finishBytes(true);

        MethodHandles.Lookup result;
        try {
            if (options == null) {
                var clazz = (Class<?>) m.invoke(cUnsafe, mLookup.lookupClass(), bytes, null);
                result = mLookup.in(clazz);
            } else {
                result = ((MethodHandles.Lookup) m.invoke(mLookup, bytes, false, options));
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            if (cause == null) {
                cause = e;
            }
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } finally {
            Type.uncache(mTypeCache, originalName);
            mInjector.unreserve(originalName);
        }

        if (hasExactConstants) {
            ConstantsRegistry.finish(this, result.lookupClass());
        }

        return result;
    }

    @Override
    public byte[] finishBytes() {
        noExactConstants();
        String name = name();
        try {
            return finishBytes(false);
        } finally {
            Type.uncache(mTypeCache, name);
            mInjector.unreserve(name);
        }
    }

    private byte[] finishBytes(boolean hidden) {
        byte[] bytes;
        try {
            var out = new BytesOut(null, 1000);
            finishTo(out, hidden);
            bytes = out.toByteArray();
        } catch (IOException e) {
            // Not expected.
            throw new RuntimeException(e);
        }

        if (DEBUG) {
            DebugWriter.write(name(), bytes);
        }

        return bytes;
    }

    @Override
    public void finishTo(OutputStream out) throws IOException {
        noExactConstants();
        String name = name();
        try {
            var bout = new BytesOut(out, 1000);
            finishTo(bout, false);
            bout.flush();
        } finally {
            Type.uncache(mTypeCache, name);
            mInjector.unreserve(name);
        }
    }

    /**
     * @param hidden when true, rename the class
     */
    private void finishTo(BytesOut out, boolean hidden) throws IOException {
        checkFinished();

        // Ensure that mSuperClass has been assigned.
        superClass();

        mFinished = -1;

        mBootstrapMethods = null;

        TheMethodMaker.doFinish(mClinitMethods);
        mClinitMethods = null;

        checkSize(mInterfaces, 65535, "Interface");
        checkSize(mFields, 65535, "Field");
        checkSize(mMethods, 65535, "Method");

        if (mMethods != null) {
            for (TheMethodMaker method : mMethods) {
                method.doFinish();
            }
        }

        if (hidden) {
            // Clean up the generated class name. It will be given a unique name by the
            // defineHiddenClass or defineAnonymousClass method.
            String name = mThisClass.mValue.mValue;
            int ix = name.lastIndexOf('-');
            if (ix > 0) {
                mThisClass.rename(mConstants.addUTF8(name.substring(0, ix)));
            }
        }

        out.writeInt(0xCAFEBABE);
        out.writeInt(0x0000_0037); // Java 11.

        mConstants.writeTo(out);

        int flags = mModifiers;
        if (!Modifier.isInterface(flags)) {
            // Set the ACC_SUPER flag for classes only.
            flags |= Modifier.SYNCHRONIZED;
        }
        out.writeShort(flags);

        out.writeShort(mThisClass.mIndex);
        out.writeShort(mSuperClass.mIndex);

        if (mInterfaces == null) {
            out.writeShort(0);
        } else {
            out.writeShort(mInterfaces.size());
            for (ConstantPool.C_Class iface : mInterfaces) {
                out.writeShort(iface.mIndex);
            }
            mInterfaces = null;
        }

        if (mFields == null) {
            out.writeShort(0);
        } else {
            out.writeShort(mFields.size());
            for (TheFieldMaker field : mFields.values()) {
                field.writeTo(out);
            }
            mFields = null;
        }

        if (mMethods == null) {
            out.writeShort(0);
        } else {
            out.writeShort(mMethods.size());
            for (TheMethodMaker method : mMethods) {
                method.writeTo(out);
            }
            mMethods = null;
        }

        writeAttributesTo(out);

        mAttributes = null;
    }

    static void checkSize(Map<?,?> c, int maxSize, String desc) {
        if (c != null) {
            checkSize(c.keySet(), maxSize, desc);
        }
    }

    static void checkSize(Collection<?> c, int maxSize, String desc) {
        if (c != null && c.size() > maxSize) {
            throw new IllegalStateException
                (desc + " count cannot exceed " + maxSize + ": " + c.size());
        }
    }

    private void checkFinished() {
        if (mFinished < 0) {
            throw new IllegalStateException("Class definition is already finished");
        }
    }

    private void noExactConstants() {
        if (mFinished == 1) {
            throw new IllegalStateException("Class has exact constants defined");
        }
    }

    @Override
    public Type type() {
        return mThisClass.mType;
    }

    Type typeFrom(Object type) {
        return Type.from(mInjector, type);
    }

    /**
     * @return bootstrap index
     */
    int addBootstrapMethod(ConstantPool.C_MethodHandle method, ConstantPool.Constant[] args) {
        if (mBootstrapMethods == null) {
            mBootstrapMethods = new Attribute.BootstrapMethods(mConstants);
            addAttribute(mBootstrapMethods);
        }
        return mBootstrapMethods.add(method, args);
    }

    boolean allowExactConstants() {
        return !mExternal;
    }

    /**
     * @return slot
     */
    int addExactConstant(Object value) {
        checkFinished();
        if (mExternal) {
            throw new IllegalStateException("Making an external class");
        }
        mFinished = 1;
        return ConstantsRegistry.add(this, value);
    }
}
