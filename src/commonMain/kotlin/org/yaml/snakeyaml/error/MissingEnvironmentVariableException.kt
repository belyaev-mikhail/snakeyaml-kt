package org.yaml.snakeyaml.error

/**
 * Indicate missing mandatory environment variable in the template
 * Used by EnvScalarConstructor
 */
class MissingEnvironmentVariableException(message: String?) : YAMLException(message)
