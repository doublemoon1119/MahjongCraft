# Code Style

## Code Comments

- **Language**: All comments and KDoc must be written in **Traditional Chinese (繁體中文)**.
- **Style**: Use objective descriptions of functionality. Avoid subjective tone or conversational language.
- **KDoc layout**: In a multiline KDoc, put `/**` on its own line. Separate the description from the first
  block tag (such as `@property`, `@param` or `@return`) with a blank comment line. No blank line is required
  between individual tags, or before tags when there is no description. Single-line KDoc remains allowed.
  The custom detekt rules enforce this layout through `detektAll`; they do not auto-format comments.
- **Completeness**: Every Kotlin declaration must have a complete comment or KDoc.
- **Constructor properties**: Document properties declared in a class's primary constructor with `@property` in the
  class KDoc. Do not place a separate KDoc immediately above each constructor property unless that placement is
  necessary to explain a declaration-specific constraint that cannot be expressed clearly in the class KDoc.
  Use `@param` for constructor parameters that are not properties.
- **Layer vocabulary**: Comments and KDoc in `:mahjong-logic`, `:mahjong-flow`, `:mahjong-ai` and
  `:mahjong-extension-api` must describe a contract in that layer's own vocabulary. Do not define what
  something is, or what it is for, in terms of a presentation surface or platform that only some
  platforms have — HUD, screens, chat, entities, ticks, or a specific mod loader. Naming a platform is
  fine in a clause that is explicitly an example or a rationale ("for example, the Minecraft adapter
  renders these on its action HUD"); it is not fine in the sentence that defines the contract.

## Kotlin Development Conventions

- **Architecture**: Follow Clean Architecture principles. Keep code concise and well-structured.
- **Test Framework**: **JUnit is strictly forbidden**. Only `kotlin.test.Test` is allowed.
- **Test Naming**: Test method names must use backtick format, e.g., `` `test sorting with different regional orders` ``.
- **Test Language**: Test method names and assertion failure messages must be written in **English**, so a failing build reads consistently with the framework's own output. Comments and KDoc inside test files still follow the Traditional Chinese rule above.

### File Organization

- Give each independently meaningful public top-level type its own file named after the type. This includes
  implicitly public declarations, `fun interface`, `value class`, `data object`, annotations and type aliases.
- Nested types stay with their owner. Small, tightly related top-level types may share a file when keeping them
  together has a concrete benefit; record the reason during review. Internal, private and local helpers are not
  subject to a mechanical one-type-per-file rule.
- Preserve packages, names, visibility, signatures and behavior when splitting existing files. Do not widen a
  private helper's visibility merely to make a split compile. Preserve sealed hierarchies, `@Serializable`,
  `@SerialName`, serializer discovery and registration, and the serialized structure.
- Keep `expect`/`actual` declarations and `actual typealias` implementations aligned across their source sets.
  Review file-level annotations, including `@file:JvmName`, `@file:JvmMultifileClass` and serialization annotations,
  before moving declarations; do not copy an annotation blindly to every destination.
- Top-level functions, extensions, properties and constants normally stay in their original file: moving them
  can change the JVM file facade even when their Kotlin package and name stay unchanged. Review Java, reflection
  and generated-code references before any exception.
- DTO files define their types; place conversion functions in `<PrimaryModel>Mapper.kt`, with both conversion
  directions and overloads for that model together. A composite model delegates to other mappers instead of
  collecting their implementations. Keep dedicated private helpers with their owning mapper; do not widen
  visibility merely to split them. Keep serializer-module construction separate from model conversion.
  Review and explicitly approve JVM facade changes when reorganizing existing conversion functions.
- Compare the public declarations before and after each split, including source-set placement. Event API ABI
  checks cover only the event surface, not every public type in the repository. Move existing KDoc with its
  declaration; do not expand a file-organization change into an unrelated documentation rewrite.

### Import Conventions

- Use file-level `import` directives and short names for Kotlin declarations. Do not use avoidable fully qualified
  names in type declarations, expressions, properties, functions, or annotations.
- When short names conflict, prefer an explicit import alias over repeating a fully qualified name in executable code.
- Fully qualified names may remain when they are intentionally stored as strings for reflection, serialization,
  interoperability, or another identifier contract, or when an import alias cannot reasonably remove an ambiguity.
- Comments and KDoc may name a fully qualified declaration when the package itself is relevant to the explanation;
  otherwise, use a resolvable documentation link or short name.

### Logging Conventions

- Fabric code creates loggers with `mahjongCraftLogger(Owner::class)` from the `platform.fabric.logging` package
  instead of calling `LoggerFactory.getLogger` directly. The logger is named `MahjongCraft/<OwnerClass>`, without
  dots, so logs that print only the last segment of a logger name still show the mod and the class.
- Declare the logger as `private val logger`. Do not use other names such as `LOGGER`.
- Declare it as an instance member by default. Put it in the companion object only when the class creates many
  instances (for example, one entity per tile) or when functions in the companion object also log.
