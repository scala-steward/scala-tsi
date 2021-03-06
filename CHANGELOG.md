## 0.4.1

* Exporter application will now be deleted after generation (#106)
* It is now recommended adding `.enablePlugins(ScalaTsiPlugin)` to your sbt project. This will become mandatory in a future release (#110)
* Improve error message on missing implicits (#111)

## 0.4.0 - 2020-09-11

* Generate typescript based on TSType, not just TSNamedType (#98)
* Rename typescriptClassesToGenerateFor sbt setting to typescriptExports


## 0.3.2 - 2020-08-28

* Support Scala and Java enumerations out of the box (#75)
* Fix bug where no error was given when no typescript mapping is available for a non-abstract sealed class (#80)

## 0.3.1 - 2020-08-22

Fixes the compiler crashing on circular references with this plugin (#67)

## 0.3.0 - 2020-06-12

* Change package from `nl.codestar` to `com.scalatsi`

Libraries will be published under both organizations on maven central for backwards-compatibility initially.

## 0.2.2 - 2020-05-29

* Add ability to rename generated types
* Output will now be generated even if parent directories do not exists yet and improve error messages
* Fixed unused import warning when generating model

## 0.2.1 - 2020-05-22

* Add support for [Literal types](https://docs.scala-lang.org/sips/42.type.html) under Scala 2.13

## 0.2.0 - 2020-05-22

Implicits aren't needed anymore in most cases, the plugin will use the standard generation by default!

* Scala 2.13 support
* Plugin now requires SBT 1.3
* Case classes and sealed trait are now automatically generated
* Sealed traits are now converted into a Typescript Discriminated Union
* Add experimental semicolon support with `typescriptStyleSemicolons` sbt setting (#62)
* `TSExternalName` was renamed to `TSTypeReference` (#43)
* Types are now always outputted in alphabetical order
* Output now has constined and no superflous whitespace


## 0.1.3 - 2018-03-08

* Add `export` to all typescript definitions for easier importing from other files (#31, Fixes #28)
* Add `TSObject` for the Typescript `object` type (#25)
* Map `scala.Any` to Typescript `any`
* Map `scala.AnyRef` and `java.lang.Object` to `object` (#25)
* Map `java.time.*` and `java.util.Date` to `string`
   changeable by overriding `val java8TimeTSType` from `JavaTSTypes` or`DefaultTSTypes` (#26)
* Map java numeric types to `Number` (#26)
* Fix collection types not automatically being converted to an array of the collection element (#27)

## 0.1.2 - 2018-06-08

* Fixes exception when using the plugin in SBT 1.1+ (#23)

## 0.1.1 - 2018-03-16

* `scala-tsi` artifact now contains all dependencies, does not depend on a macro project any more

## 0.1.0 - 2018-03-16

Initial Release of scala-tsi

Features:
* Sbt plugin to generate a typescript file from your domain definition
* Matchings (`TSType`) from a good set of built-in Java and Scala types
* macro to generate typescript interfaces from your case classes
* DSL to write your own typescript definitions
* AST that can model the whole typescript type system (afaik)
