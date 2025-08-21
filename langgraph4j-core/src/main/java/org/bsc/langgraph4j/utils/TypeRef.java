package org.bsc.langgraph4j.utils;

import java.lang.reflect.*;
import java.util.Optional;

/**
 * This class is inspired by TypeReference from Jackson Library
 * It is used to pass full generics type information, and
 * avoid problems with type erasure (that basically removes most
 * usable type references from runtime Class objects).
 * It is based on ideas from
 * <a href="http://gafter.blogspot.com/2006/12/super-type-tokens.html">http://gafter.blogspot.com/2006/12/super-type-tokens.html</a>,
 * Additional idea (from a suggestion made in comments of the article)
 * is to require bogus implementation of <code>Comparable</code>
 * (any such generic interface would do, as long as it forces a method
 * with generic type to be implemented).
 * to ensure that a Type argument is indeed given.
 * <p>
 *  Usage is by sub-classing: here is one way to instantiate reference
 *  to generic type <code>List&lt;Integer></code>:
 * <pre>
 *   var TypeRef = new TypeRef&lt;List&lt;Integer>>() { };
 * </pre>
 * which can be passed to methods that accept TypeReference.
 *
 * @param <T>
 */
public abstract class TypeRef<T> implements Comparable<TypeRef<T>> {
    protected final Type _type;

    protected TypeRef() {
        Type superClass = this.getClass().getGenericSuperclass();
        if (superClass instanceof Class) {
            throw new IllegalArgumentException("Internal error: TypeReference constructed without actual type information");
        } else {
            this._type = ((ParameterizedType)superClass).getActualTypeArguments()[0];
        }
    }

    /**
     * Gets the captured generic type.
     *
     * @return the {@link Type} representing the generic type T.
     */
    public Type getType() {
        return this._type;
    }

    /**
     * Safely casts the given object to the generic type {@code T} of this TypeRef.
     * This method first checks if the object is an instance of the raw type (erasure)
     * of {@code T}.
     *
     * @param obj the object to cast.
     * @return an {@link Optional} containing the cast object if the cast is successful,
     *         otherwise an empty {@link Optional}.
     */
    @SuppressWarnings("unchecked")
    public Optional<T> cast(Object obj ) {
        return Types.erasureOf(this._type)
                .filter( c -> c.isInstance(obj) )
                .map( c -> (T) obj )
                ;
    }

    /**
     * Gets the raw {@link Class} of the generic type {@code T}.
     * This is the type erasure of the captured generic type.
     *
     * @return an {@link Optional} containing the raw class of {@code T},
     *         or an empty {@link Optional} if the erasure cannot be determined.
     */
    @SuppressWarnings("unchecked")
    public Optional<Class<T>> erasureOf() {
        return Types.erasureOf(this._type).map( c -> (Class<T>)c );
    }

    /**
     * The only reason we define this method (and require implementation
     * of <code>Comparable</code>) is to prevent constructing a
     * reference without type information.
     */
    @Override
    public int compareTo(TypeRef<T> o) {
        return 0;
    }
}