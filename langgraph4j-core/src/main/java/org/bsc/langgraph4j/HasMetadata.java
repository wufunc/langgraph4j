package org.bsc.langgraph4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Optional.ofNullable;

public interface HasMetadata<B extends HasMetadata.Builder<B>> {

    /**
     * return metadata value for key
     *
     * @param key given metadata key
     * @return metadata value for key if any
     *
     */
    Optional<Object> metadata( String key );

    /**
     * return metadata value for key
     *
     * @param key given metadata key
     * @return metadata value for key if any
     * @deprecated use {@link #metadata(String)} instead
     */
    @Deprecated( forRemoval = true )
    default Optional<Object> getMetadata(String key ) {
        return metadata(key);
    };

    class Builder<B extends Builder<B>> {
        private Map<String,Object> metadata;

        public Map<String,Object> metadata() {
            return ofNullable(metadata).map(Map::copyOf).orElseGet(Map::of);
        }

        protected Builder() {}

        protected Builder( Map<String,Object> metadata ) {
            this.metadata = metadata;
        }

        @SuppressWarnings("unchecked")
        public B addMetadata( String key, Object value ) {
            if( metadata == null ) {
                // Lazy initialization of metadata map
                metadata = new HashMap<>();
            }

            metadata.put( key, value);

            return (B)this;
        };
    }

}

