package org.bsc.langgraph4j.utils;

import java.lang.reflect.*;
import java.util.Optional;

public interface Types {

    /**
     * Traverses the class hierarchy of the given class to find the first
     * {@link ParameterizedType}. This is useful for finding generic superclass information.
     *
     * @param clazz the class to inspect.
     * @return an {@link Optional} containing the first {@link ParameterizedType} found,
     *         or an empty {@link Optional} if none is found.
     */
    static Optional<ParameterizedType> parameterizedType(Class<?> clazz) {
        Type superClass = clazz.getGenericSuperclass();
        do {
            if (superClass instanceof ParameterizedType parameterizedType) {
                return Optional.of(parameterizedType);
            }
            superClass = erasureOf(superClass).map(Class::getGenericSuperclass).orElse(null);

        } while( superClass!=null );

        return Optional.empty();
    }

    /**
     * Gets the raw {@link Class} for a given {@link Type}.
     * This utility method handles various {@link Type} subtypes like {@link Class},
     * {@link ParameterizedType}, {@link GenericArrayType}, {@link TypeVariable},
     * and {@link WildcardType}.
     *
     * @param t the type to get the erasure of. Can be null.
     * @return an {@link Optional} containing the raw class, or an empty {@link Optional}
     *         if the erasure cannot be determined or the input is null.
     */
    static Optional<Class<?>> erasureOf(Type t) {
        if (t instanceof Class<?> c) {
            return Optional.of(c);
        }
        if (t instanceof ParameterizedType pt) {
            return Optional.of((Class<?>) pt.getRawType());
        }
        if (t instanceof GenericArrayType gat) {
            return erasureOf(gat.getGenericComponentType())
                    .map( comp -> Array.newInstance(comp, 0).getClass() );
        }
        if (t instanceof TypeVariable<?> tv) {
            Type[] bounds = tv.getBounds();
            return bounds.length == 0 ? Optional.empty() : erasureOf(bounds[0]);
        }
        if (t instanceof WildcardType wt) {
            Type[] upper = wt.getUpperBounds();
            return upper.length == 0 ? Optional.empty() : erasureOf(upper[0]);
        }
        return Optional.empty();
    }


}
