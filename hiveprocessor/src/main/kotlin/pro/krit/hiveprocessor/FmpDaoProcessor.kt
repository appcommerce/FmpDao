// Copyright (c) 2021 Aleksandr Minkin aka Rasalexman (sphc@yandex.ru)
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software
// and associated documentation files (the "Software"), to deal in the Software without restriction,
// including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
// subject to the following conditions:
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
// WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
// IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
// WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
// THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package pro.krit.hiveprocessor

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import pro.krit.hiveprocessor.annotations.FmpDao
import pro.krit.hiveprocessor.annotations.FmpDatabase
import pro.krit.hiveprocessor.annotations.FmpLocalDao
import pro.krit.hiveprocessor.annotations.FmpQuery
import pro.krit.hiveprocessor.common.LocalDaoFields
import pro.krit.hiveprocessor.data.BindData
import pro.krit.hiveprocessor.data.TypeData
import pro.krit.hiveprocessor.provider.IFmpDatabase
import java.io.IOException
import java.util.*
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic
import kotlin.properties.Delegates


@AutoService(Processor::class)
class FmpDaoProcessor : AbstractProcessor() {

    companion object {
        private const val CLASS_POSTFIX = "Impl"

        private const val DAO_PACKAGE_NAME = "pro.krit.generated.dao"
        private const val DATABASE_PACKAGE_NAME = "pro.krit.generated.database"

        private const val EXTENSIONS_PATH = "pro.krit.hiveprocessor.extensions"
        private const val QUERY_EXECUTER_PATH = "pro.krit.hiveprocessor.common"
        private const val QUERY_EXECUTER_NAME = "QueryExecuter"

        private const val HYPER_HIVE_BASE_CLASSE_NAME = "HyperHiveDatabase"

        private const val INIT_CREATE_TABLE = "createTable"

        private const val REQUEST_STATEMENT = "request"
        private const val REQUEST_NAME = "requestWithParams"
        private const val REQUEST_ASYNC_STATEMENT = "requestAsync"
        private const val REQUEST_ASYNC_NAME = "requestWithParamsAsync"

        private const val FUNC_MEMBER_STATEMENT = "this.%M()"
        private const val FUNC_MEMBER_PARAMS_STATEMENT = "this.%M"

        private const val FIELD_PROVIDER = "fmpDatabase"
        private const val FIELD_RESOURCE = "resourceName"
        private const val FIELD_TABLE = "tableName"
        private const val FIELD_IS_DELTA = "isDelta"
        private const val FIELD_DAO_FIELDS = "localDaoFields"

        private const val LIST_RETURN_TYPE = "java.util.List"
        private const val SUSPEND_QUALIFIER = "kotlin.coroutines.Continuation"

        private const val KOTLIN_PATH = "kotlin"
        private const val KOTLIN_LIST_PATH = "kotlin.collections"
        private const val KOTLIN_LIST_NAME = "List"

        private const val NULL_INITIALIZER = "null"
        private const val TAG_CLASS_NAME = "%T"

        private const val QUERY_VALUE = "val query: String = "
        private const val QUERY_RETURN = "return %T.executeQuery(this, query)"

        private const val FILE_COMMENT =
            "This file was generated by HyperHiveProcessor. Do not modify!"


        /** Element Utilities, obtained from the processing environment */
        private var ELEMENT_UTILS: Elements by Delegates.notNull()

        /** Type Utilities, obtained from the processing environment */
        private var TYPE_UTILS: Types by Delegates.notNull()
    }

    /* Processing Environment helpers */
    private var filer: Filer by Delegates.notNull()

    /* message helper */
    private var messager: Messager by Delegates.notNull()

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        filer = processingEnv.filer
        messager = processingEnv.messager
        ELEMENT_UTILS = processingEnv.elementUtils
        TYPE_UTILS = processingEnv.typeUtils
    }

    override fun process(
        annotations: MutableSet<out TypeElement>,
        roundEnv: RoundEnvironment
    ): Boolean {
        val startTime = System.currentTimeMillis()
        println("HyperHiveProcessor started")
        val modulesMap = mutableListOf<BindData>()
        // Create files for FmpDao annotation
        val fmpResult = collectAnnotationData(
            roundEnv.getElementsAnnotatedWith(FmpDao::class.java),
            modulesMap,
            ::getDataFromFmpDao
        )
        // Create files for FmpLocalDao annotation
        val fmpLocalResult = collectAnnotationData(
            roundEnv.getElementsAnnotatedWith(FmpLocalDao::class.java),
            modulesMap,
            ::getDataFromFmpLocalDao
        )
        // If we has generated files for database without errors
        if (!fmpResult && !fmpLocalResult) {
            processDaos(modulesMap)

            val databases = roundEnv.getElementsAnnotatedWith(FmpDatabase::class.java)
            databases?.forEach { currentDatabase ->
                processDatabase(currentDatabase, modulesMap)
            }
        }

        println("HyperHiveProcessor finished in `${System.currentTimeMillis() - startTime}` ms")
        return false
    }

    private fun processDatabase(databaseElement: Element, daoList: List<BindData>) {
        checkElementForRestrictions("FmpDatabase", databaseElement)

        val annotationType = databaseElement.asType()
        val databaseData = ClassName.bestGuess(annotationType.toString())

        val className = databaseData.simpleName
        val packName = databaseData.packageName
        val fileName = className.createFileName()

        val superClassName = ClassName(packName, className)
        val classTypeSpec = TypeSpec.objectBuilder(fileName)
        classTypeSpec.superclass(superClassName)

        // Extended classes only for Interfaces
        val extendedTypeMirrors = TYPE_UTILS.directSupertypes(databaseElement.asType())
        if (extendedTypeMirrors != null && extendedTypeMirrors.size > 1) {
            val extendedElements = extendedTypeMirrors.mapToInterfaceElements()
            extendedElements.forEach {
                createFunctions(classTypeSpec, it, daoList)
            }
        }
        createDatabaseExtendedFunction(classTypeSpec, databaseElement, daoList)
        // than create function for database
        createFunctions(classTypeSpec, databaseElement, daoList)

        val fileBuilder = FileSpec.builder(DATABASE_PACKAGE_NAME, fileName)
            .addComment(FILE_COMMENT)
            .addType(classTypeSpec.build())

        val file = fileBuilder.build()
        try {
            file.writeTo(filer)
        } catch (e: IOException) {
            val message = java.lang.String.format("Unable to write file: %s", e.message)
            messager.printMessage(Diagnostic.Kind.ERROR, message)
        }
    }

    private fun createDatabaseExtendedFunction(classTypeSpec: TypeSpec.Builder,
                                               element: Element,
                                               daoList: List<BindData>) {
        val extendedTypeMirrors = TYPE_UTILS.directSupertypes(element.asType())
        if (extendedTypeMirrors != null && extendedTypeMirrors.size > 1) {
            val extendedElements = extendedTypeMirrors.mapToInterfaceElements()
            extendedElements.forEach {
                createFunctions(classTypeSpec, it, daoList)
                createDatabaseExtendedFunction(classTypeSpec, it, daoList)
            }
        }
    }

    private fun createFunctions(
        classTypeSpec: TypeSpec.Builder,
        element: Element,
        daoList: List<BindData>
    ) {
        val methods = element.enclosedElements
        methods.forEach { enclose ->
            if (enclose.isAbstractMethod()) {
                val (returnPack, returnClass) = enclose.asType().toString().getPackAndClass()
                val funcName = enclose.simpleName.toString()
                val returnedClass = ClassName(returnPack, returnClass)
                val returnElementData = daoList.find { it.mainData.className == returnClass }

                returnElementData?.let {
                    val returnedClassName = ClassName(DAO_PACKAGE_NAME, it.fileName)

                    val propName = funcName + CLASS_POSTFIX
                    val prop =
                        PropertySpec.builder(propName, returnedClassName.copy(nullable = true))
                            .mutable()
                            .addModifiers(KModifier.PRIVATE)
                            .initializer(NULL_INITIALIZER)
                            .build()

                    classTypeSpec.addProperty(prop)

                    val statementIf = "if($propName == $NULL_INITIALIZER) "
                    val statementCreate = "$propName = $TAG_CLASS_NAME($FIELD_PROVIDER = this) "
                    val statementReturn = "return $propName!!"

                    val funcSpec = FunSpec.builder(funcName)
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(returnedClass)
                        .beginControlFlow(statementIf)
                        .addStatement(statementCreate, returnedClassName)
                        .endControlFlow()
                        .addStatement(statementReturn)
                        .build()

                    classTypeSpec.addFunction(funcSpec)
                }
            }
        }
    }

    private fun processDaos(moduleElements: List<BindData>) {
        moduleElements.forEach { bindData ->
            val classFileName = bindData.fileName
            val mainClassName = ClassName(bindData.mainData.packName, bindData.mainData.className)

            val classTypeSpec =
                TypeSpec.classBuilder(classFileName).addSuperinterface(mainClassName)
                    .primaryConstructor(constructorFunSpec(bindData))
                    .addProperties(createProperties(bindData))

            if(bindData.parameters.isNotEmpty()) {
                val localParams = bindData.parameters
                val requestFunc = createRequestFunction(localParams, REQUEST_NAME, isAsync = false)
                val requestFuncAsync = createRequestFunction(localParams, REQUEST_ASYNC_NAME, isAsync = true)
                classTypeSpec.addFunction(requestFunc.build())
                classTypeSpec.addFunction(requestFuncAsync.build())
            }

            val functs = bindData.element.enclosedElements
            functs.forEach { enclose ->
                val queryAnnotation = enclose.getAnnotation(FmpQuery::class.java)
                if (queryAnnotation != null && enclose.isAbstractMethod()) {
                    val returnType = enclose.asType().toString()
                    val isSuspend = returnType.contains(SUSPEND_QUALIFIER)
                    val (returnPack, returnClass) = returnType.getPackAndClass()
                    val funcName = enclose.simpleName.toString()
                    val returnedClass = returnType.createReturnType(returnPack, returnClass)

                    val funcSpec = FunSpec.builder(funcName)
                    var query = queryAnnotation.query.replaceTablePattern(returnClass, bindData)

                    val parameters = enclose.takeParameters(isSuspend)
                    parameters.forEach { property ->
                        val propName = property.toString()
                        val propertyClassName =
                            property.asType().toString().getPackAndClass().second

                        val kotlinPropClass = propertyClassName.capitalizeFirst()
                        val propertyClass = ClassName(KOTLIN_PATH, kotlinPropClass)
                        val queryProperty = propName.screenParameter(propertyClassName)
                        val replacedProperty = ":$propName"

                        query = query.replace(replacedProperty, queryProperty)

                        val parameterSpec = ParameterSpec.builder(propName, propertyClass).build()
                        funcSpec.addParameter(parameterSpec)
                    }

                    val queryExecuter = ClassName(QUERY_EXECUTER_PATH, QUERY_EXECUTER_NAME)

                    val statementQuery = "\"${query}\""

                    funcSpec.apply {
                        if (isSuspend) {
                            addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                        } else {
                            addModifiers(KModifier.OVERRIDE)
                        }
                    }.returns(returnedClass).apply {
                        addStatement(QUERY_VALUE)
                        addStatement(statementQuery)
                        addStatement(QUERY_RETURN, queryExecuter)
                    }

                    classTypeSpec.addFunction(funcSpec.build())
                }
            }

            val file = FileSpec.builder(DAO_PACKAGE_NAME, classFileName)
                .addComment(FILE_COMMENT)
                .addType(classTypeSpec.build())
                .build()
            try {
                file.writeTo(filer)
            } catch (e: IOException) {
                val message = java.lang.String.format("Unable to write file: %s", e.message)
                messager.printMessage(Diagnostic.Kind.ERROR, message)
            }
        }
    }

    private fun createRequestFunction(parameters: List<String>, funName: String, isAsync: Boolean): FunSpec.Builder {
        val funcSpec = FunSpec.builder(funName)
        var mapOfParams = "%M("
        val paramsSize = parameters.size - 1
        parameters.forEachIndexed { index, paramName ->
            val param = paramName.lowercase()
            val propertyClass = ClassName(KOTLIN_PATH, "Any")
            val parameterSpec = ParameterSpec.builder(param, propertyClass).build()
            funcSpec.addParameter(parameterSpec)
            mapOfParams += "\"$paramName\" to $param"
            mapOfParams += if(index < paramsSize) ", " else ""
        }
        mapOfParams += ")"

        val statement = "return $FUNC_MEMBER_PARAMS_STATEMENT(params = $mapOfParams)"
        val returnType = ClassName("com.mobrun.plugin.models", "BaseStatus")
        val updateFuncName = if(isAsync) {
            REQUEST_ASYNC_STATEMENT
        } else {
            REQUEST_STATEMENT
        }
        return funcSpec.addStatement(
            statement,
            MemberName(EXTENSIONS_PATH, updateFuncName),
            MemberName("kotlin.collections", "mapOf")
        ).returns(returnType).apply {
            if(isAsync) {
                addModifiers(KModifier.SUSPEND)
            }
        }
    }

    private fun createProperties(bindData: BindData): List<PropertySpec> {
        val hyperHiveProviderProp =
            PropertySpec.builder(FIELD_PROVIDER, IFmpDatabase::class)
                .initializer(FIELD_PROVIDER)
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .build()

        val resourceNameProp = PropertySpec.builder(FIELD_RESOURCE, String::class)
            .initializer(FIELD_RESOURCE)
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .build()

        val parameterNameProp = PropertySpec.builder(FIELD_TABLE, String::class)
            .initializer(FIELD_TABLE)
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .build()

        val isCachedProp = PropertySpec.builder(FIELD_IS_DELTA, Boolean::class)
            .initializer(FIELD_IS_DELTA)
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .build()

        return if (bindData.isLocal) {
            val isLocalProp = PropertySpec.builder(
                FIELD_DAO_FIELDS,
                LocalDaoFields::class.asTypeName().copy(nullable = true)
            ).mutable()
                .initializer(FIELD_DAO_FIELDS)
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .build()
            listOf(
                hyperHiveProviderProp,
                resourceNameProp,
                parameterNameProp,
                isCachedProp,
                isLocalProp
            )
        } else {
            listOf(hyperHiveProviderProp, resourceNameProp, parameterNameProp, isCachedProp)
        }
    }

    private fun constructorFunSpec(bindData: BindData): FunSpec {
        val resourceName = ParameterSpec.builder(FIELD_RESOURCE, String::class).apply {
            if (bindData.resourceName.isNotEmpty()) {
                defaultValue("\"${bindData.resourceName}\"")
            }
        }.build()

        val parameterName = ParameterSpec.builder(FIELD_TABLE, String::class).apply {
            if (bindData.tableName.isNotEmpty()) {
                defaultValue("\"${bindData.tableName}\"")
            }
        }.build()

        val isCached = ParameterSpec.builder(FIELD_IS_DELTA, Boolean::class)
            .defaultValue("${bindData.isDelta}")
            .build()

        val hyperHiveProvider =
            ParameterSpec.builder(FIELD_PROVIDER, IFmpDatabase::class)
                .build()

        return FunSpec.constructorBuilder()
            .addParameter(hyperHiveProvider)
            .addParameter(resourceName)
            .addParameter(parameterName)
            .addParameter(isCached)
            .apply {
                if (bindData.isLocal) {
                    val isLocalProp = ParameterSpec.builder(
                        FIELD_DAO_FIELDS,
                        LocalDaoFields::class.asTypeName().copy(nullable = true)
                    )
                        .defaultValue(NULL_INITIALIZER)
                        .build()
                    addParameter(isLocalProp)
                    if(bindData.createTableOnInit) {
                        addStatement(
                            FUNC_MEMBER_STATEMENT,
                            MemberName(EXTENSIONS_PATH, INIT_CREATE_TABLE)
                        )
                    }
                }
            }
            .build()
    }

    private fun getDataFromFmpDao(element: Element): BindData {
        val annotation = element.getAnnotation(FmpDao::class.java)
        return createAnnotationData(
            annotationName = "FmpDao",
            element = element,
            resourceName = annotation.resourceName,
            tableName = annotation.tableName,
            isDelta = annotation.isDelta,
            parameters = annotation.parameters,
            isLocal = false,
            createTableOnInit = false
        )
    }

    private fun getDataFromFmpLocalDao(element: Element): BindData {
        val annotation = element.getAnnotation(FmpLocalDao::class.java)
        return createAnnotationData(
            annotationName = "FmpLocalDao",
            element = element,
            resourceName = annotation.resourceName,
            tableName = annotation.tableName,
            parameters = emptyArray(),
            createTableOnInit = annotation.createTableOnInit,
            isDelta = false,
            isLocal = true
        )
    }

    private fun createAnnotationData(
        annotationName: String,
        element: Element,
        resourceName: String,
        tableName: String,
        parameters: Array<String>,
        createTableOnInit: Boolean,
        isDelta: Boolean = false,
        isLocal: Boolean = false
    ): BindData {


        checkElementForRestrictions(element = element, annotationName = annotationName)

        val annotationType = element.asType()
        val elementClassName = ClassName.bestGuess(annotationType.toString())
        val className = elementClassName.simpleName
        val fileName = className.createFileName()

        return BindData(
            element = element,
            fileName = fileName,
            createTableOnInit = createTableOnInit,
            parameters = parameters.toList(),
            mainData = TypeData(
                packName = elementClassName.packageName,
                className = elementClassName.simpleName
            ),
            resourceName = resourceName,
            tableName = tableName,
            isDelta = isDelta,
            isLocal = isLocal
        )
    }

    private fun checkElementForRestrictions(annotationName: String, element: Element) {
        val annotationType = element.asType()
        val hasKindError = if(annotationName == "FmpDatabase") {
            element.kind == ElementKind.INTERFACE
        } else {
            element.kind == ElementKind.CLASS
        }
        val hasAbstractError = hasKindError || !element.modifiers.contains(Modifier.ABSTRACT)
        if(hasAbstractError) {
            throw IllegalStateException(
                "$annotationType with $annotationName annotation should be an interface and " +
                        "implement I${annotationName}.kt to be correctly processed"
            )
        }

        if(!checkExtendedInterface(element, annotationName)) {
            throw IllegalStateException(
                "$annotationType with $annotationName annotation should implement I${annotationName}.kt to be correctly processed"
            )
        }
    }

    private fun checkExtendedInterface(element: Element, annotationName: String): Boolean {
        var hasElement = false
        val extendedTypeMirrors = TYPE_UTILS.directSupertypes(element.asType())
        val extendedElements = extendedTypeMirrors.mapToInterfaceElements()
        if(!extendedElements.isNullOrEmpty()) {
            extendedElements.forEach {
                hasElement = it.simpleName.toString().contains(annotationName)
                if(!hasElement) {
                    hasElement = checkExtendedInterface(it, annotationName)
                }
            }
        }
        return hasElement
    }

    // Take extended interfaces for implement abstract methods
    private fun List<TypeMirror>.mapToInterfaceElements(): List<Element> {
        return this.mapNotNull { typeMirror ->
            typeMirror.takeIf {
                val currentClassName = it.toString().getPackAndClass().second
                !currentClassName.contains(HYPER_HIVE_BASE_CLASSE_NAME)
            }?.run {
                val typeElement = TYPE_UTILS.asElement(this)
                // Only for Interfaces
                typeElement.takeIf { it.kind == ElementKind.INTERFACE }
            }
        }
    }

    private fun Element.takeParameters(isSuspend: Boolean): List<Element> {
        val allParams = (this as? ExecutableElement)?.parameters.orEmpty()
        return if (isSuspend) {
            if (allParams.isNotEmpty() && allParams.size > 1) {
                allParams.subList(0, allParams.size - 1)
            } else emptyList()
        } else {
            allParams
        }
    }

    private fun Element.isAbstractMethod(): Boolean {
        return this.kind == ElementKind.METHOD && this.modifiers.contains(Modifier.ABSTRACT)
    }

    private fun String.createReturnType(returnPack: String, returnClass: String): TypeName {
        val isList = this.contains(LIST_RETURN_TYPE)
        return if (isList) {
            val list = ClassName(KOTLIN_LIST_PATH, KOTLIN_LIST_NAME)
            list.parameterizedBy(ClassName(returnPack, returnClass))
        } else {
            ClassName(returnPack, returnClass)
        }
    }

    private fun String.getPackAndClass(): Pair<String, String> {
        val withoutSuspend = this.withoutSuspend()
        val withoutBraces = withoutSuspend.replace("()", "")
        val replaced = withoutBraces.splitArray()

        val splitted = replaced.split(".")
        val className = splitted.last()
        val packName = splitted.subList(0, splitted.size - 1).joinToString(".")
        return packName to className
    }

    private fun String.withoutSuspend(): String {
        val isSuspend = this.contains(SUSPEND_QUALIFIER)
        return if (isSuspend) {
            val indexStart = this.indexOf(LIST_RETURN_TYPE)
            val lastIndex = this.lastIndexOf(">")
            this.substring(indexStart, lastIndex)
        } else {
            this
        }
    }

    private fun String.replaceTablePattern(returnClass: String, bindData: BindData): String {
        val tablePattern = ":$returnClass"
        return if (this.contains(tablePattern)) {
            val tableName = "${bindData.resourceName}_${bindData.tableName}"
            this.replace(tablePattern, tableName)
        } else this
    }

    private fun String.splitArray(): String {
        val ifArray = this.contains(LIST_RETURN_TYPE)
        return if (ifArray) {
            this.split("<")[1].replace(">", "")
        } else {
            this
        }
    }

    private fun String.screenParameter(className: String): String {
        return if (className == "String") {
            "\'$$this\'"
        } else {
            "$$this"
        }
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(
            FmpDao::class.java.canonicalName,
            FmpLocalDao::class.java.canonicalName,
            FmpDatabase::class.java.canonicalName
        )
    }

    override fun getSupportedOptions(): Set<String?>? {
        return Collections.singleton("org.gradle.annotation.processing.aggregating")
        //return Collections.singleton("org.gradle.annotation.processing.isolating")
    }

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

    @Suppress("SameParameterValue")
    private fun collectAnnotationData(
        elementsSet: Set<Element>,
        items: MutableList<BindData>,
        elementDataHandler: (Element) -> BindData
    ): Boolean {
        elementsSet.forEach { element ->
            val kind = element.kind
            if (kind == ElementKind.METHOD && kind != ElementKind.CLASS && kind != ElementKind.INTERFACE) {
                messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "Only classes and interfaces can be annotated as @FmpDao, @FmpLocalDao or @FmpDatabase"
                )
                return true
            }
            val bindingData = elementDataHandler(element)
            items.add(bindingData)
        }
        return false
    }

    private fun String.capitalizeFirst(): String {
        return replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun String.createFileName(): String {
        var className = this
        val classNameFirstChar = className.first()
        if (classNameFirstChar == 'I' || classNameFirstChar == 'i') {
            className = className.substring(1)
        }
        return className + CLASS_POSTFIX
    }

}