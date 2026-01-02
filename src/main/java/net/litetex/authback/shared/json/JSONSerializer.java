package net.litetex.authback.shared.json;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.mojang.authlib.minecraft.client.ObjectMapper;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.response.ProfileSearchResultsResponse;
import com.mojang.util.ByteBufferTypeAdapter;
import com.mojang.util.UUIDTypeAdapter;


public final class JSONSerializer
{
	public static final Gson GSON = new GsonBuilder()
		.registerTypeAdapter(Instant.class, InstantConverter.INSTANCE)
		.setPrettyPrinting()
		.create();
	
	// Same as ObjectMapper#create but faster
	// Use only for game internal stuff that is not related to the mod
	public static final ObjectMapper FAST_OBJECT_MAPPER = new ObjectMapper(new GsonBuilder()
		.registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
		.registerTypeAdapter(Instant.class, InstantConverter.INSTANCE)
		.registerTypeHierarchyAdapter(ByteBuffer.class, new ByteBufferTypeAdapter().nullSafe())
		.registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer())
		.registerTypeAdapter(ProfileSearchResultsResponse.class, new ProfileSearchResultsResponse.Serializer())
		.create());
	
	private JSONSerializer()
	{
	}
	
	static final class InstantConverter implements JsonSerializer<Instant>, JsonDeserializer<Instant>
	{
		static final InstantConverter INSTANCE = new InstantConverter();
		
		static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;
		
		// Using a cache speeds up repeated the serialization of Instant by 4-10x
		private final Map<Instant, String> formatCache = Collections.synchronizedMap(new WeakHashMap<>());
		
		private InstantConverter()
		{
		}
		
		@Override
		public JsonElement serialize(final Instant src, final Type typeOfSrc, final JsonSerializationContext context)
		{
			return new JsonPrimitive(this.formatCache.computeIfAbsent(src, FORMATTER::format));
		}
		
		@Override
		public Instant deserialize(
			final JsonElement json, final Type typeOfT,
			final JsonDeserializationContext context)
		{
			return FORMATTER.parse(json.getAsString(), Instant::from);
		}
	}
}
