// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.android;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.devtools.build.android.proto.SerializeFormat;

import com.android.resources.ResourceType;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.CheckReturnValue;
import javax.annotation.concurrent.Immutable;

/**
 * Represents a fully qualified name for an android resource.
 *
 * Each resource name consists of the resource package, name, type, and qualifiers.
 */
@Immutable
public class FullyQualifiedName implements DataKey, Comparable<FullyQualifiedName> {
  public static final String DEFAULT_PACKAGE = "res-auto";
  private static final Joiner DASH_JOINER = Joiner.on('-');

  private final String pkg;
  private final ImmutableList<String> qualifiers;
  private final ResourceType resourceType;
  private final String resourceName;

  /**
   * Returns a string path representation of the FullyQualifiedName.
   *
   * Non-values Android Resource have a well defined file layout: From the resource directory, they
   * reside in &lt;resource type&gt;[-&lt;qualifier&gt;]/&lt;resource name&gt;[.extension]
   *
   * @param sourceExtension The extension of the resource represented by the FullyQualifiedName
   * @return A string representation of the FullyQualifiedName with the provided extension.
   */
  public String toPathString(String sourceExtension) {
    // TODO(corysmith): Does the extension belong in the FullyQualifiedName?
    return Paths.get(
            DASH_JOINER.join(
                ImmutableList.<String>builder()
                    .add(resourceType.getName())
                    .addAll(qualifiers)
                    .build()),
            resourceName + sourceExtension)
        .toString();
  }

  public String name() {
    return resourceName;
  }

  /**
   * A factory for parsing an generating FullyQualified names with qualifiers and package.
   */
  public static class Factory {
    private static final Pattern PARSING_REGEX =
        Pattern.compile("(?:(?<package>[^:]+):){0,1}(?<type>[^/]+)/(?<name>\\w+)");
    public static final String INVALID_QUALIFIED_NAME_MESSAGE =
        String.format(
            "%%s is not a valid qualified name. "
                + "It should be in the pattern [package:]{%s}/resourcename",
            Joiner.on(",").join(ResourceType.values()));
    private final List<String> qualifiers;
    private final String pkg;

    private Factory(List<String> qualifiers, String pkg) {
      this.qualifiers = qualifiers;
      this.pkg = pkg;
    }

    public static Factory from(List<String> qualifiers, String pkg) {
      return new Factory(qualifiers, pkg);
    }

    public static Factory from(List<String> qualifiers) {
      return from(qualifiers, DEFAULT_PACKAGE);
    }

    public FullyQualifiedName create(ResourceType resourceType, String resourceName) {
      return create(resourceType, resourceName, pkg);
    }

    public FullyQualifiedName create(ResourceType resourceType, String resourceName, String pkg) {
      return FullyQualifiedName.of(pkg, qualifiers, resourceType, resourceName);
    }

    /**
     * Parses a FullyQualifiedName from a string.
     *
     * @param raw A string in the expected format from
     *     [&lt;package&gt;:]&lt;ResourceType.name&gt;/&lt;resource name&gt;.
     * @throws IllegalArgumentException when the raw string is not valid qualified name.
     */
    public FullyQualifiedName parse(String raw) {
      String[] typeAndName = raw.split("/");
      Preconditions.checkArgument(typeAndName.length == 2, "Invalid type and name: %s", raw);
      Matcher matcher = PARSING_REGEX.matcher(raw);
      if (!matcher.matches()) {
        throw new IllegalArgumentException(String.format(INVALID_QUALIFIED_NAME_MESSAGE, raw));
      }
      String parsedPackage = matcher.group("package");
      ResourceType resourceType = ResourceType.getEnum(matcher.group("type"));
      String resourceName = matcher.group("name");

      if (resourceType == null || resourceName == null) {
        throw new IllegalArgumentException(String.format(INVALID_QUALIFIED_NAME_MESSAGE, raw));
      }
      return FullyQualifiedName.of(
          parsedPackage == null ? pkg : parsedPackage, qualifiers, resourceType, resourceName);
    }
  }

  public static boolean isOverwritable(FullyQualifiedName name) {
    return !(name.resourceType == ResourceType.ID || name.resourceType == ResourceType.STYLEABLE);
  }

  /**
   * Creates a new FullyQualifiedName with sorted qualifiers.
   *
   * @param pkg The resource package of the name. If unknown the default should be "res-auto"
   * @param qualifiers The resource qualifiers of the name, such as "en" or "xhdpi".
   * @param resourceType The resource type of the name.
   * @param resourceName The resource name of the name.
   * @return A new FullyQualifiedName.
   */
  public static FullyQualifiedName of(
      String pkg, List<String> qualifiers, ResourceType resourceType, String resourceName) {
    return new FullyQualifiedName(
        pkg, Ordering.natural().immutableSortedCopy(qualifiers), resourceType, resourceName);
  }

  public static FullyQualifiedName fromProto(SerializeFormat.DataKey protoKey) {
    return of(
        protoKey.getKeyPackage(),
        protoKey.getQualifiersList(),
        ResourceType.valueOf(protoKey.getResourceType()),
        protoKey.getKeyValue());
  }

  private FullyQualifiedName(
      String pkg,
      ImmutableList<String> qualifiers,
      ResourceType resourceType,
      String resourceName) {
    this.pkg = pkg;
    this.qualifiers = qualifiers;
    this.resourceType = resourceType;
    this.resourceName = resourceName;
  }

  /** Creates a FullyQualifiedName from this one with a different package. */
  @CheckReturnValue
  public FullyQualifiedName replacePackage(String newPackage) {
    if (pkg.equals(newPackage)) {
      return this;
    }
    // Don't use "of" because it ensures the qualifiers are sorted -- we already know
    // they are sorted here.
    return new FullyQualifiedName(newPackage, qualifiers, resourceType, resourceName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pkg, qualifiers, resourceType, resourceName);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FullyQualifiedName)) {
      return false;
    }
    FullyQualifiedName other = getClass().cast(obj);
    return Objects.equals(pkg, other.pkg)
        && Objects.equals(resourceType, other.resourceType)
        && Objects.equals(resourceName, other.resourceName)
        && Objects.equals(qualifiers, other.qualifiers);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(getClass())
        .add("pkg", pkg)
        .add("qualifiers", qualifiers)
        .add("resourceType", resourceType)
        .add("resourceName", resourceName)
        .toString();
  }

  @Override
  public int compareTo(FullyQualifiedName other) {
    if (!pkg.equals(other.pkg)) {
      return pkg.compareTo(other.pkg);
    }
    if (!resourceType.equals(other.resourceType)) {
      return resourceType.compareTo(other.resourceType);
    }
    if (!resourceName.equals(other.resourceName)) {
      return resourceName.compareTo(other.resourceName);
    }
    // TODO(corysmith): Figure out a more performant stable way to keep a stable order.
    if (!qualifiers.equals(other.qualifiers)) {
      if (qualifiers.size() != other.qualifiers.size()) {
        return qualifiers.size() - other.qualifiers.size();
      }
      // This works because the qualifiers are sorted on creation.
      return qualifiers.toString().compareTo(other.qualifiers.toString());
    }
    return 0;
  }

  @Override
  public void serializeTo(OutputStream out, int valueSize) throws IOException {
    SerializeFormat.DataKey.newBuilder()
        .setKeyPackage(pkg)
        .setValueSize(valueSize)
        .setResourceType(resourceType.getName().toUpperCase())
        .addAllQualifiers(qualifiers)
        .setKeyValue(resourceName)
        .build()
        .writeDelimitedTo(out);
  }
}
