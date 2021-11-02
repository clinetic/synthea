package org.mitre.synthea.export;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.List;

import org.mitre.synthea.engine.State;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

/**
 * Class that manages the export of a Synthea Person and their related information into a JSON
 * format. This is a somewhat "raw" export, as the information is only lightly edited before export.
 * In other words, it's pretty close to an object dump.
 */
public class JSONExporter {

  /**
   * Export the given Person object into a String full of JSON.
   * @param person to export
   * @return a lot of JSON in a String
   */
  public static String export(Person person) {
    Gson gson = new GsonBuilder()
        .excludeFieldsWithModifiers(Modifier.STATIC, Modifier.TRANSIENT, Modifier.VOLATILE)
        .addSerializationExclusionStrategy(new SyntheaExclusionStrategy())
        .registerTypeHierarchyAdapter(State.class, new StateSerializer())
        .registerTypeHierarchyAdapter(Person.class, new PersonSerializer())
        .create();
    return gson.toJson(person);
  }

  public static class PersonSerializer implements JsonSerializer<Person> {
    @Override
    public JsonElement serialize(Person src, Type typeOfSrc, JsonSerializationContext context) {
      JsonObject personOut = new JsonObject();
      personOut.add("seed", new JsonPrimitive(src.seed));
      personOut.add("lastUpdated", new JsonPrimitive(src.lastUpdated));
      personOut.add("coverage", context.serialize(src.coverage));
      JsonObject attributes = new JsonObject();
      src.attributes.forEach((key, value) -> {
        boolean keepEntry = true;
        if (key.startsWith("ehr_") || key.contains("lookup")) {
          keepEntry = false;
        } else if ((!Config.getAsBoolean("exporter.json.include_module_history"))
            && isModuleHistory(value)) {
          keepEntry = false;
        }

        if (keepEntry) {
          attributes.add(key, context.serialize(value));
        }
      });
      personOut.add("attributes", attributes);
      if (src.hasMultipleRecords) {
        personOut.add("records", context.serialize(src.records));
      } else {
        personOut.add("record", context.serialize(src.record));
      }
      return personOut;
    }

    private boolean isModuleHistory(Object obj) {
      if (List.class.isAssignableFrom(obj.getClass())) {
        List things = (List) obj;
        return things.stream().allMatch(t -> State.class.isAssignableFrom(t.getClass()));
      }

      return false;
    }
  }

  public static class StateSerializer implements JsonSerializer<State> {

    @Override
    public JsonElement serialize(State src, Type typeOfSrc, JsonSerializationContext context) {
      JsonObject stateOut = new JsonObject();
      stateOut.add("state_name", new JsonPrimitive(src.name));
      stateOut.add("entered", new JsonPrimitive(src.entered));
      if (src.exited != null) {
        stateOut.add("exited", new JsonPrimitive(src.exited));
      }
      return stateOut;
    }
  }

  public static class SyntheaExclusionStrategy implements ExclusionStrategy {
    @Override
    public boolean shouldSkipField(FieldAttributes f) {
      return f.getAnnotation(JSONSkip.class) != null;
    }

    @Override
    public boolean shouldSkipClass(Class<?> clazz) {
      return org.mitre.synthea.engine.Module.class.isAssignableFrom(clazz);
    }
  }
}
