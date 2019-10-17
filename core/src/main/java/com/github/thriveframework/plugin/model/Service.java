package com.github.thriveframework.plugin.model;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Service {
    String name;
    ImageDefinition definition;
    @Singular("env")
    Map<String, String> environment = new HashMap<>();
    @Singular
    Set<Port> ports = new HashSet<>();
    @Singular("expose")
    Set<Integer> exposed = new HashSet<>();
    @Singular("startupDependency")
    Set<String> startupDependencies = new HashSet<>();
    @Singular("runtimeDependency")
    Set<String> runtimeDependencies = new HashSet<>();
    String command;

    //todo comment out of date; ctor can be useless, we have copy-with-new-name one
    //required so that Gradle can create named domain objects (see ThrivePackageExtension)
    public Service(String name){
        this(name,
            null,
            new HashMap<>(),
            new HashSet<>(),
            new HashSet<>(),
            new HashSet<>(),
            new HashSet<>(),
            null
        );
    }

    public Service(Service example){
        this(
            example.name,
            example.definition != null ?
                new ImageDefinition(example.definition.getComposeKey(), example.definition.getImageSpec()) :
                null,
            new HashMap<>(example.environment),
            example.ports.stream().map(p -> Port.between(p.getExternal(), p.getInternal()) ).collect(toSet()),
            new HashSet<>(example.exposed),
            new HashSet<>(example.startupDependencies),
            new HashSet<>(example.runtimeDependencies),
            example.command
        );
    }

    public Service(String name, Service example){
        this(example);
        this.name = name;
    }

    public Service overwrittenBy(Service other){
        Service out = new Service(this);
        if (other.name != null){
            out.name = other.name;
        }
        if (other.definition != null){
            out.definition = other.definition;
        }
        out.environment.putAll(other.environment);
        out.ports.addAll(other.ports); //todo assert ports.out is unique
        out.exposed.addAll(other.exposed); //todo assert ports.out is unique
        out.startupDependencies.addAll(other.startupDependencies);
        out.runtimeDependencies.addAll(other.runtimeDependencies);
        if (other.command != null)
            out.command = other.command;
        return out;
    }

    /**
     * Is any field of this service non-null and not empty? Name is ignored.
     */
    public boolean empty(){
        return definition == null &&
            (environment == null || environment.isEmpty()) &&
            (ports == null || ports.isEmpty()) &&
            (startupDependencies == null || startupDependencies.isEmpty()) &&
            (runtimeDependencies == null || runtimeDependencies.isEmpty()) &&
            (command == null || command.isEmpty());
    }
}
