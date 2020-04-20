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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Principal;
import java.security.ProtectionDomain;

/**
 * 
 *
 * @author Brian S O'Neill
 */
class ClassInjector extends ClassLoader {
    private static final Map<Object, ClassInjector> cInjectors = new ConcurrentHashMap<>();

    private final Map<String, Boolean> mReservedNames = new WeakHashMap<>();
    private final ProtectionDomain mDomain;

    ClassInjector(ClassLoader parent, ProtectionDomain domain) {
        super(parent);
        mDomain = prepareDomain(domain, this);
    }

    ClassInjector(ProtectionDomain domain) {
        super();
        mDomain = prepareDomain(domain, this);
    }

    private static ProtectionDomain prepareDomain(ProtectionDomain domain, ClassLoader loader) {
        if (domain == null) {
            return null;
        }

        return new ProtectionDomain(domain.getCodeSource(),
                                    domain.getPermissions(),
                                    loader,
                                    domain.getPrincipals());
    }

    // Prevent name collisions while multiple threads are defining classes by reserving the name.
    boolean reserveName(String name, boolean explicit) {
        synchronized (mReservedNames) {
            if (mReservedNames.put(name, Boolean.TRUE) != null && !explicit) {
                return false;
            }
        }

        // If explicit and name has already been reserved, don't immediately return false. This
        // allows the class to be defined if an earlier injected class instance was abandoned.
        // A duplicate class definition can still be attempted later, which is converted to an
        // IllegalStateException by the define method.

        try {
            loadClass(name);
        } catch (ClassNotFoundException e) {
            return true;
        } catch (LinkageError e) {
            // Class by same name exists, but it is broken.
        }

        return false;
    }

    Class<?> define(String name, byte[] b) {
        try {
            Class<?> clazz;
            if (mDomain == null) {
                clazz = defineClass(name, b, 0, b.length);
            } else {
                clazz = defineClass(name, b, 0, b.length, mDomain);
            }
            resolveClass(clazz);
            return clazz;
        } catch (LinkageError e) {
            // Replace duplicate name definition with a better exception.
            try {
                loadClass(name);
                throw new IllegalStateException("Class already defined: " + name);
            } catch (ClassNotFoundException e2) {
            }
            throw e;
        } finally {
            synchronized (mReservedNames) {
                mReservedNames.remove(name);
            }
        }
    }

    static Reservation reserve(String className,
                               ClassLoader parentLoader,
                               ProtectionDomain domain,
                               boolean explicit)
    {
        if (className == null) {
            if (explicit) {
                throw new IllegalArgumentException("Explicit class name not provided");
            }
            className = ClassMaker.class.getName();
        }

        if (parentLoader == null) {
            parentLoader = ClassMaker.class.getClassLoader();
            if (parentLoader == null) {
                parentLoader = ClassLoader.getSystemClassLoader();
            }
        }

        final Object injectorKey = createInjectorKey(className, parentLoader, domain);

        ClassInjector injector = cInjectors.get(injectorKey);
        if (injector == null) {
            injector = parentLoader == null
                ? new ClassInjector(domain) : new ClassInjector(parentLoader, domain);
            ClassInjector existing = cInjectors.putIfAbsent(injectorKey, injector);
            if (existing != null) {
                injector = existing;
            }
        }

        if (explicit) {
            return new Reservation(injector, className);
        }

        var rnd = ThreadLocalRandom.current();

        for (int tryCount = 0; tryCount < 1000; tryCount++) {
            long id = rnd.nextInt();

            // Use a small identifier if possible, making it easier to read stack traces and
            // decompiled classes.
            switch (tryCount) {
            case 0:
                id &= 0xffL;
                break;
            case 1: case 2: case 3: case 4:
                id &= 0xffffL;
                break;
            default:
                id &= 0xffffffffL;
                break;
            }

            String mangled = className + '$' + id;

            if (injector.reserveName(mangled, false)) {
                return new Reservation(injector, mangled);
            }
        }

        throw new InternalError("Unable to create unique class name");
    }

    private static Object createInjectorKey(String className, ClassLoader parentLoader,
                                            ProtectionDomain domain)
    {
        String packageName;
        {
            int index = className.lastIndexOf('.');
            if (index < 0) {
                packageName = "";
            } else {
                packageName = className.substring(0, index);
            }
        }

        // ProtectionDomain doesn't have an equals method, so break it apart and add the
        // elements to the composite key.

        Object domainKey = null;
        Object csKey = null;
        Object permsKey = null;
        Object principalsKey = null;

        if (domain != null) {
            domainKey = "";
            csKey = domain.getCodeSource();

            PermissionCollection pc = domain.getPermissions();
            if (pc != null) {
                List<Permission> permList = Collections.list(pc.elements());
                if (permList.size() == 1) {
                    permsKey = permList.get(0);
                } else if (permList.size() > 1) {
                    permsKey = new HashSet<Permission>(permList);
                }
            }

            Principal[] principals = domain.getPrincipals();
            if (principals != null && principals.length > 0) {
                if (principals.length == 1) {
                    principalsKey = principals[0];
                } else {
                    Set<Principal> principalSet = new HashSet<>(principals.length);
                    for (Principal principal : principals) {
                        principalSet.add(principal);
                    }
                    principalsKey = principalSet;
                }
            }
        }

        Object[] composite = new Object[] {
            parentLoader, packageName, domainKey, csKey, permsKey, principalsKey
        };

        return new Key(composite);
    }

    private static class Key {
        private final Object[] mComposite;
        private final int mHash;

        Key(Object[] composite) {
            mComposite = composite;
            mHash = Arrays.deepHashCode(composite);
        }

        @Override
        public int hashCode() {
            return mHash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof Key) {
                return Arrays.deepEquals(mComposite, ((Key) obj).mComposite);
            }
            return false;
        }
    }

    static class Reservation {
        final ClassInjector mInjector;
        final String mClassName;

        Reservation(ClassInjector injector, String className) {
            mInjector = injector;
            mClassName = className;
        }
    }
}