package com.scalatsi

import scala.reflect.macros.blackbox

private[scalatsi] class Macros(val c: blackbox.Context) {
  import c.universe.*
  private val macroUtil = new MacroUtil[c.type](c)
  import macroUtil.*

  /** Tree to use to get a TSType[T] */
  private def getTSType[TSType[T]](T: Type)(implicit tsTypeTag: c.WeakTypeTag[TSType[?]]): Tree = {

    if (T.typeArgs.isEmpty) {
      q"""_root_.com.scalatsi.TSType.getOrGenerate[$T]"""
    } else {
      val allTypeArguments = findNestedTypeParameters(T)
      val targImplicits    =
        allTypeArguments.distinct
          // Prevent generating an `implicit val` again if it is already in scope before macro expansion, otherwise
          // we might get ambiguous implicit values
          .filter(targ => !implicitIsDefined(properType[TSType](targ)))
          .zipWithIndex
          .map({ case (targ, i) =>
            q"""implicit val ${TermName(s"targ$i")} = TSType.getOrGenerate[$targ]"""
          })
          .toList
      q"""{
          import _root_.com.scalatsi.TSType
          ..$targImplicits
          TSType.getOrGenerate[$T]
        }"""
    }
  }

  /** Generating TSTypes based on implicit conversion might fail if we still have to generate a nested type.
    * For example `TSType[Seq[CaseClass]]` will fail if `TSType[CaseClass]` doesn't exist yet.
    *
    * To combat this, traverse all generic type parameters bottom-up, and get or generate them.
    */
  private def findNestedTypeParameters(T: Type, first: Boolean = true): Iterator[Type] =
    T.typeArgs.iterator.flatMap(t => findNestedTypeParameters(t, first = false)) ++ (if (first) Iterator() else Iterator(T))

  private def mapToNever[T: c.WeakTypeTag]: Tree =
    q"""{
       import _root_.com.scalatsi.TSNamedType
       import _root_.com.scalatsi.TypescriptType.{TSAlias, TSNever}
       TSNamedType(TSAlias(${tsName[T]}, TSNever))
     }"""

  /** Change a Type into a "IType" for a class/trait, and "Type" otherwise */
  private def tsName[T: c.WeakTypeTag]: String = {
    val symbol = c.weakTypeOf[T].typeSymbol

    val prefix = Option(symbol)
      .collect({ case clsSymbol if clsSymbol.isClass => clsSymbol.asClass })
      .filterNot(_.isDerivedValueClass)
      .map(_ => "I")

    prefix.getOrElse("") + symbol.name.toString
  }

  private def circularRefWarning(T: c.Type, which: String): Tree = {
    c.warning(
      c.enclosingPosition,
      s"""
         |Circular reference or very deep nesting encountered while searching for $which[$T]
         |Please break the cycle or lower the nesting by locally defining an implicit TSType like so:
         |implicit val tsType...: $which[...] = {
         |  implicit val tsA: $which[$T] = TSType.external("I$T") // name of your "$T" typescript type here
         |  $which.getOrGenerate[...]
         |}
         |for more help see https://github.com/scala-tsi/scala-tsi#circular-references
         |""".stripMargin
    )
    q"""com.scalatsi.TSType(com.scalatsi.TypescriptType.TSLiteralString("circular reference error"))"""
  }

  def getImplicitMappingOrGenerateDefault[T: c.WeakTypeTag, TSType[_]](implicit tsTypeTag: c.WeakTypeTag[TSType[?]]): Tree = {
    lookupOptionalImplicit(properType[T, TSType]) match {
      case Right(Some(value))      => value
      case Right(None)             => generateDefaultMapping[T, TSType]
      case Left(CircularReference) => circularRefWarning(c.weakTypeOf[T], "TSType")
    }
  }

  def getImplicitInterfaceMappingOrGenerateDefault[T, TSType[_], TSIType[_]](implicit
      tt: c.WeakTypeTag[T],
      tsTypeTag: c.WeakTypeTag[TSType[?]],
      tsiTypeTag: c.WeakTypeTag[TSIType[?]]
  ): Tree = {
    lookupOptionalImplicit(properType[T, TSIType]) match {
      case Right(Some(value))      => value
      case Right(None)             => generateInterfaceFromCaseClass[T, TSType]
      case Left(CircularReference) => circularRefWarning(c.weakTypeOf[T], "TSIType")
    }
  }

  /** Generate an implicit not found message */
  private def notFound[T: c.WeakTypeTag]: Tree = {
    val msg = s"Could not find TSType[${c.weakTypeOf[T]}] in scope and could not generate it"
    c.warning(c.enclosingPosition, s"$msg. Did you create and import it?")
    q"""com.scalatsi.TSType(com.scalatsi.TypescriptType.TSLiteralString($msg))"""
  }

  private def generateDefaultMapping[T: c.WeakTypeTag, TSType[_]](implicit tsTypeTag: c.WeakTypeTag[TSType[?]]): Tree = {
    val T      = c.weakTypeOf[T]
    val symbol = T.typeSymbol

    if (!symbol.isClass) {
      notFound[T]
    } else {
      val classSymbol = symbol.asClass
      if (classSymbol.isCaseClass)
        generateInterfaceFromCaseClass[T, TSType]
      else if (isSealedTraitOrAbstractClass(classSymbol))
        generateUnionFromSealedTrait[T, TSType]
      else notFound[T]
    }
  }

  private def primaryConstructor(T: Type): MethodSymbol =
    T.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor =>
        if (!m.isPublic)
          c.abort(c.enclosingPosition, s"Only classes with public primary constructor are supported. Found: $T")
        m
    }.get

  private def caseClassFieldsTypes(T: Type): Seq[(String, Type)] = {
    val paramLists = primaryConstructor(T).paramLists
    val params     = paramLists.head

    if (paramLists.size > 1)
      c.abort(c.enclosingPosition, s"Only one parameter list classes are supported. Found: $T")

    params.foreach { p =>
      if (!p.isPublic)
        c.abort(
          c.enclosingPosition,
          s"Only classes with all public constructor arguments are supported. Found: $T"
        )
    }

    params.map { field =>
      (field.name.toTermName.decodedName.toString, field.infoIn(T))
    }
  }

  def generateInterfaceFromCaseClass[T: c.WeakTypeTag, TSType[_]](implicit tsTypeTag: c.WeakTypeTag[TSType[?]]): Tree = {
    val T      = c.weakTypeOf[T]
    val symbol = getClassOrTraitSymbol(T)

    if (!symbol.isCaseClass)
      c.abort(c.enclosingPosition, s"Expected case class, but found: $T")

    val members = caseClassFieldsTypes(T) map {
      case (name, none) if none <:< typeOf[None.type] =>
        q"($name, TSNull)"
      case (name, optional) if optional <:< typeOf[Option[?]] =>
        val typeArg = optional.typeArgs.head
        q"($name, ${getTSType[TSType](typeArg)} | TSUndefined)"
      case (name, tpe) =>
        q"($name, ${getTSType[TSType](tpe)}.get)"
    }

    q"""{
     import _root_.com.scalatsi.TypescriptType.{TSUndefined, TSInterface}
     import _root_.com.scalatsi.{TSNamedType, TSIType}
     import _root_.scala.collection.immutable.ListMap
     TSIType(TSInterface(${tsName[T]}, ListMap(
       ..$members
     )))
    }"""
  }

  private def getClassOrTraitSymbol(T: Type): ClassSymbol = {
    val symbol = T.typeSymbol
    if (!symbol.isClass)
      c.abort(c.enclosingPosition, s"Expected class or trait, but found $T")
    symbol.asClass
  }

  private def isSealedTraitOrAbstractClass(symbol: ClassSymbol): Boolean =
    symbol.isSealed && (symbol.isTrait || symbol.isAbstract)

  def generateUnionFromSealedTrait[T: c.WeakTypeTag, TSType[_]](implicit tsTypeTag: c.WeakTypeTag[TSType[?]]): Tree = {
    val T      = c.weakTypeOf[T]
    val symbol = getClassOrTraitSymbol(T)

    if (!isSealedTraitOrAbstractClass(symbol))
      c.abort(c.enclosingPosition, s"Expected sealed trait or sealed abstract class, but found: $T")

    val name = symbol.name.toString

    symbol.knownDirectSubclasses.toSeq match {
      case Seq() =>
        val symbolType =
          if (symbol.isTrait) "trait"
          else if (symbol.isClass && symbol.isAbstract) "abstract class"
          else "type"
        c.warning(c.enclosingPosition, s"Sealed $symbolType $T has no known subclasses, could not generate union")
        mapToNever[T]
      case Seq(singleChild) =>
        q"""{
         import _root_.com.scalatsi.TypescriptType
         import _root_.com.scalatsi.TSNamedType
         import TypescriptType.TSAlias
         TSNamedType(TSAlias($name, TypescriptType.nameOrType(${getTSType[TSType](singleChild.asType.toType)}.get)))
        }"""
      case children =>
        val operands = children map { symbol =>
          q"TypescriptType.nameOrType(${getTSType[TSType](symbol.asType.toType)}.get, discriminator = Some(${symbol.name.toString}))"
        }

        q"""{
        import _root_.com.scalatsi.TypescriptType
        import TypescriptType.{TSAlias, TSUnion}
        import _root_.com.scalatsi.TSNamedType
        import _root_.scala.collection.immutable.Vector
        TSNamedType(TSAlias($name, TSUnion(Vector(..$operands))))
      }"""
    }
  }
}
