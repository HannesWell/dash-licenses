/*************************************************************************
 * Copyright (c) 2024 Simon Reis
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which accompanies this
 * distribution, and is available at https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *************************************************************************/

package org.eclipse.dash.licenses.cli;

import java.io.Reader;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.dash.licenses.ContentId;
import org.eclipse.dash.licenses.IContentId;
import org.eclipse.dash.licenses.InvalidContentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

/**
 * This class is responsible for reading a pnpm-lock.yaml generated by the PNPM
 * Package Manager and extracting content IDs. A content ID represents a unique
 * identifier for a package or dependency.
 *
 * The class implements the IDependencyListReader interface.
 *
 * The class uses the SnakeYAML library to parse the pnpm-lock.yaml file.
 * Content ID is extracted only from the keys of the packages section of the
 * pnpm-lock file. The main magic is done by the regex: KEY_PATTERN.
 *
 **/
public class PnpmPackageLockFileReader implements IDependencyListReader {
	final Logger logger = LoggerFactory.getLogger(PnpmPackageLockFileReader.class);
	private static final Pattern KEY_PATTERN = Pattern
			.compile("^'?(\\/?(?<namespace>@[^\\/]+)\\/)?\\/?(?<name>[^\\/@]+)[@\\/](?<version>[^(@\\/'\\n]+)(?=\\()?");
	private final Reader input;

	/**
	 * Constructs a new PnpmPackageLockFileReader with the specified input stream.
	 *
	 * @param input the input stream of the PNPM package-lock file
	 */
	public PnpmPackageLockFileReader(Reader input) {
		this.input = input;
	}

	/**
	 * Returns a collection of unique content IDs extracted from the PNPM
	 * package-lock file.
	 *
	 * @return a collection of content IDs
	 */
	@Override
	public Collection<IContentId> getContentIds() {
		return contentIds().distinct().collect(Collectors.toList());
	}

	/**
	 * Parses the specified key and returns the corresponding content ID.
	 *
	 * @param key the key to parse
	 * @return the content ID extracted from the key
	 */
	public IContentId getId(String key) {
		var matcher = KEY_PATTERN.matcher(key);
		if (matcher.find()) {
			var namespace = Optional.ofNullable(matcher.group("namespace")).orElse("-");
			var name = matcher.group("name");
			var version = matcher.group("version");
			return ContentId.getContentId("npm", "npmjs", namespace, name, version);
		}

		logger.debug("Invalid content id: {}", key);
		return new InvalidContentId(key);
	}

	/**
	 * Returns a stream of content IDs extracted from the PNPM package-lock file. We
	 * only read the keys of the packages.
	 *
	 * packages:
	 *
	 * /@babel/preset-modules@0.1.6-no-external-plugins(@babel/core@7.23.2):
	 *
	 * @return a stream of content IDs
	 */
	@SuppressWarnings("unchecked")
	public Stream<IContentId> contentIds() {
		Yaml yaml = getYamlParser();
		Map<String, Object> load;
		try {
			load = yaml.load(input);
			Map<String, Object> packages = (Map<String, Object>) load.getOrDefault("packages", new LinkedHashMap<>());
			return packages.keySet().stream().map(this::getId);
		} catch (Exception e) {
			logger.debug("Error reading content of package-lock.yaml file", e);
			throw new RuntimeException("Error reading content of package-lock.yaml file");
		}
	}

	/**
	 * Returns a YAML parser with custom options.
	 *
	 * @return a YAML parser
	 */
	private static Yaml getYamlParser() {
		Representer representer = new Representer(new DumperOptions());
		representer.getPropertyUtils().setSkipMissingProperties(true);
		LoaderOptions loaderOptions = new LoaderOptions();
		SafeConstructor constructor = new SafeConstructor(loaderOptions);
		return new Yaml(constructor, representer, new DumperOptions());
	}
}
