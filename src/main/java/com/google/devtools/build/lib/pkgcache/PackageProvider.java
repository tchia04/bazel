// Copyright 2014 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.pkgcache;

import com.google.devtools.build.lib.cmdline.PackageIdentifier;
import com.google.devtools.build.lib.events.EventHandler;
import com.google.devtools.build.lib.packages.NoSuchPackageException;
import com.google.devtools.build.lib.packages.Package;

/**
 * API for retrieving packages. Implementations generally load packages to fulfill requests.
 *
 * <p><b>Concurrency</b>: Implementations should be thread safe for {@link #getPackage}.
 */
public interface PackageProvider extends TargetProvider {

  /**
   * Returns the {@link Package} named "packageName". If there is no such package (e.g.
   * {@code isPackage(packageName)} returns false), throws a {@link NoSuchPackageException}.
   *
   * <p>The returned package may contain lexical/grammatical errors, in which
   * case <code>pkg.containsErrors() == true</code>.  Such packages may be
   * missing some rules.  Any rules that are present may soundly be used for
   * builds, though.
   *
   * @param eventHandler the eventHandler on which to report warning and errors; if the package
   *        has been loaded by another thread, this eventHandler won't see any warnings or errors
   * @param packageName a legal package name.
   * @throws NoSuchPackageException if the package could not be found.
   * @throws InterruptedException if the package loading was interrupted.
   */
  Package getPackage(EventHandler eventHandler, PackageIdentifier packageName)
      throws NoSuchPackageException, InterruptedException;

  /**
   * Returns whether a package with the given name exists. That is, returns whether all the
   * following hold
   * <ol>
   *   <li>{@code packageName} is a valid package name</li>
   *   <li>there is a BUILD file for the package</li>
   *   <li>the package is not considered deleted via --deleted_packages</li>
   * </ol>
   *
   * <p> If these don't hold, then attempting to read the package with {@link #getPackage} may fail
   * or may return a package containing errors.
   *
   * @param eventHandler the eventHandler on which to report warnings and errors
   * @param packageName the name of the package.
   */
  boolean isPackage(EventHandler eventHandler, PackageIdentifier packageName);
}
