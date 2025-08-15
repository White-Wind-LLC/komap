# komap-samples

Examples of using Komap annotations and KSP processor.

Highlights:

* **Basic mapping** with `@Komap` and property renaming via `@MapName`
* **Target-only properties** handled with `@IgnoreInMapping` and passed as arguments in generated mappers (single item)
  or via a lambda when mapping collections
* **Custom conversions** via `@KomapProvider` (e.g., `Email` ↔ `String`, `Long` ↔ `AttributeId`)
* **Factory-based construction** via `@KomapFactory` with qualifier selection (e.g., `AttributeGroup.FACTORY_NAME`,
  `AttributeItem.FACTORY_NAME`)

Build the project to generate mapping functions:

```bash
./gradlew :komap-samples:kspCommonMainKotlinMetadata
```

Open the sample files under `src/jvmMain/kotlin/ua/wwind/komap/samples` and look for calls like:

+ `user.toApiUser(role = "USER")` and `users.toApiUser { "ADMIN" }` in `basic/BasicSample.kt`
+ `genderItem.toAttributeItem()` and `groupAttribute.toAttributeGroup()` in `factory/FactorySample.kt`

These are generated extension functions. You can navigate to their declarations in your IDE to inspect the generated
APIs.

Providers used in samples are in `providers/` (`EmailProviders.kt`, `IdProviders.kt`).

Note: Generated sources are written under the module's KSP folder (e.g.,
`komap-samples/build/generated/ksp/metadata/commonMain/`).
