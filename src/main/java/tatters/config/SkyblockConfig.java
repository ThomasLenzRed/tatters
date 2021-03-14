/*
 * This file is part of Tatters.
 * Copyright (c) 2021, warjort and others, All rights reserved.
 *
 * Tatters is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tatters is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Tatters.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package tatters.config;

import static tatters.TattersMain.log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import tatters.TattersMain;

public class SkyblockConfig extends Config {

    private static final List<List<String>> DEFAULT_LAYERS = Collections.emptyList();

    private static final Path SKYBLOCKS_DIR = CONFIG_DIR.resolve("skyblocks");
    private static Map<String, String> skyblockConfigs = Maps.newConcurrentMap();

    public final String enabledComment = "Set to false to not load this skyblock";
    public boolean enabled = true;

    public final String nameComment = "The name to display to the user, can be a translation key";
    public String name;

    public final String layersComment = "layers, rows, columns of characters specifying block keys, blank is air, ! is the spawn point";
    public List<List<String>> layers = DEFAULT_LAYERS;

    public final String mappingComment = "block key definitions: key -> block, properties, nbt";
    public Map<Character, SkyblockBlockDefinition> mapping = Maps.newLinkedHashMap();

    public void validate() {
        if (!enabled)
            return;
        final Set<Character> blockKeys = Sets.newHashSet();
        for (List<String> row : this.layers) {
            for (String column : row) {
                if (column == null) {
                    throw new IllegalArgumentException("Null data in layers - misplaced syntax?");
                }
                column.chars().forEach(c -> blockKeys.add((char) c));
            }
        }
        blockKeys.remove(' ');
        blockKeys.remove('!');
        if (blockKeys.isEmpty()) {
            throw new IllegalArgumentException("No blocks defined in layers");
        }

        final Set<Character> keys = Sets.newHashSet();
        this.mapping.forEach((key, blockDef) -> {
            if (key == ' ') {
                throw new IllegalArgumentException("Cannot map the space character, it is reserved for the air block");
            }
            if (key == '!') {
                throw new IllegalArgumentException("Cannot map the ! character, it is reserved for the spawn point");
            }
            keys.add(key);
            if (blockDef.block == null || blockDef.block.isEmpty()) {
                throw new IllegalArgumentException("No block defined for " + key);
            }
            blockDef.validate();
        });

        blockKeys.removeAll(keys);
        if (!blockKeys.isEmpty()) {
            throw new IllegalArgumentException("Blocks have no mapping: " + blockKeys);
        }
    }

    static SkyblockConfig getSkyblockConfig(final String name) {
        final Path file = SKYBLOCKS_DIR.resolve(name);
        final SkyblockConfig result = readFile(file, SkyblockConfig.class);
        if (!result.enabled)
            throw new IllegalStateException("Tried to load disabled skyblock: " + name);
        result.validate();
        return result;
    }

    public Map<String, String> getSkyblockConfigs() {
        return Collections.unmodifiableMap(skyblockConfigs);
    }

    static void copySkyblocks() {
        mkdirs(SKYBLOCKS_DIR);

        final Path skyblocks = TattersMain.getModContainer().getPath("assets/tatters/skyblocks");
        try {
            Files.list(skyblocks).filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
                try {
                    final Path destination = SKYBLOCKS_DIR.resolve(path.getFileName().toString());
                    if (!Files.exists(destination)) {
                        final SkyblockConfig skyblock = readFile(path, SkyblockConfig.class);
                        skyblock.validate();
                        writeFile(destination, skyblock);
                    }
                } catch (Exception e) {
                    log.warn("Not copying: " + path, e);
                }
            });
        }
        catch (Exception e) {
            throw new RuntimeException("Error copying skyblocks", e);
        }
    }

    static void loadSkyblocks() {
        final Map<String, String> map = Maps.newConcurrentMap();
        try {
            Files.list(SKYBLOCKS_DIR).filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
                try {
                    final SkyblockConfig skyblock = readFile(path, SkyblockConfig.class);
                    if (skyblock.enabled) {
                        final String pathName = path.getFileName().toString();
                        skyblock.validate();
                        if (skyblock.name == null || skyblock.name.isEmpty()) {
                            skyblock.name = pathName;
                        }
                        map.put(pathName, skyblock.name);
                    }
                } catch (Exception e) {
                    log.warn("Not loading: " + path, e);
                }
            });
            skyblockConfigs = map;
        }
        catch (Exception e) {
            throw new RuntimeException("Error loading skyblocks", e);
        }
    }
}
