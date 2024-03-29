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
import com.mobrun.plugin.api.HyperHive
import com.mobrun.plugin.api.request_assistant.CustomParameter
import com.mobrun.plugin.api.request_assistant.NumeratedFields
import com.mobrun.plugin.api.request_assistant.PrimaryKey
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import pro.krit.hhivecore.base.IDao
import pro.krit.hhivecore.base.IRequest
import pro.krit.hhivecore.common.DaoFieldsData
import pro.krit.hhivecore.data.BindData
import pro.krit.hhivecore.data.TypeData
import pro.krit.hhivecore.annotations.*
import pro.krit.hhivecore.extensions.*
import pro.krit.hhivecore.provider.IFmpDatabase
import pro.krit.hhivecore.request.ObjectRawStatus
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

@DelicateKotlinPoetApi(message = "use with clear mind")
@AutoService(Processor::class)
class FmpProcessor : AbstractProcessor() {

    companion object {

        private const val FMP_DATABASE_NAME = "FmpDatabase"

        private const val DAO_PACKAGE_NAME = "pro.krit.generated.dao"
        private const val DATABASE_PACKAGE_NAME = "pro.krit.generated.database"
        private const val REQUEST_PACKAGE_NAME = "pro.krit.generated.request"

        private const val EXTENSIONS_PATH = "pro.krit.hhivecore.extensions"
        private const val QUERY_EXECUTER_PATH = "pro.krit.hhivecore.common"
        private const val QUERY_EXECUTER_NAME = "QueryExecuter"

        private const val BASE_FMP_DATABASE_NAME = "AbstractFmpDatabase"
        private const val FMP_DATABASE_ANNOTATION_NAME = "FmpDatabase"
        private const val FMP_FIELDS_DAO_NAME = "FieldsDao"

        private const val INIT_CREATE_TABLE = "createTable"

        private const val REQUEST_STATEMENT = "request"
        private const val REQUEST_NAME = "requestWithParams"
        //private const val REQUEST_ASYNC_STATEMENT = "requestAsync"
        //private const val REQUEST_ASYNC_NAME = "requestWithParamsAsync"

        //private const val FUNC_CREATE_PARAMS_NAME = "createParams"
        private const val FUNC_CREATE_PARAMS_MAP_NAME = "createParamsMap"
        private const val FUNC_GET_PARAMETER_NAME = "getParameterName"

        private const val FMP_DAO_NAME = "FmpDao"
        private const val FMP_LOCAL_DAO_NAME = "FmpLocalDao"
        private const val FMP_BASE_NAME = "IFmpDatabase"

        private const val TAG_MEMBER_FULL = "%M()"
        private const val TAG_MEMBER_HALF = "%M("
        private const val FUNC_MEMBER_STATEMENT = "this.%M()"
        private const val FUNC_MEMBER_PARAMS_STATEMENT = "this.%M"
        private const val FUNC_MEMBER_STATEMENT_GENERIC = "this.%M<"
        private const val FUNC_MEMBER_STATEMENT_GENERIC_CLOSE = ">()"

        private const val FIELD_PROVIDER = "fmpDatabase"
        private const val FIELD_HYPER_HIVE = "hyperHive"
        private const val FIELD_DEFAULT_HEADERS = "defaultHeaders"
        private const val FIELD_RESOURCE_NAME = "resourceName"
        private const val FIELD_TABLE = "tableName"
        private const val FIELD_IS_DELTA = "isDelta"
        private const val FIELD_DAO_FIELDS = "fieldsData"
        private const val FIELD_PARAMS = "params"
        private const val FIELD_PARAMS_REPLACE = "(params = %s)"
        private const val FIELD_PARAMS_GET = "params.getOrNull(%s).orEmpty()"
        //private const val FIELD_PARAMS_GET_NULL = "params.getOrNull(%s)"

        private const val MOBRUN_MODEL_PATH = "com.mobrun.plugin.models"
        private const val MOBRUN_SELECTABLE_NAME = "StatusSelectTable"
        private const val MOBRUN_BASE_NAME = "BaseStatus"

        private const val NULL_INITIALIZER = "null"
        private const val RETURN_STATEMENT = "return"
        private const val TAG_CLASS_NAME = "%T"

        private const val QUERY_VALUE = "val query: String = "
        private const val QUERY_RETURN = "return %T.executeQuery(this, query)"

        private const val FILE_COMMENT =
            "This file was generated by FmpProcessor. Do not modify!"

        private const val PARAMS_COMMENT_FIRST = "This function was autogenerated."
        private const val PARAMS_COMMENT_SECOND =
            " Use annotation field - 'parameters' to add request params"
        private const val PARAMS_COMMENT_THIRD = " Please specify %s vararg param: "


        /** Element Utilities, obtained from the processing environment */
        private var ELEMENT_UTILS: Elements by Delegates.notNull()

        /** Type Utilities, obtained from the processing environment */
        private var TYPE_UTILS: Types by Delegates.notNull()
    }

    /* Processing Environment helpers */
    private var filer: Filer by Delegates.notNull()

    /* message helper */
    private var messager: Messager by Delegates.notNull()

    // names of all props
    private val allProperties = mutableListOf<String>()

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(
            FmpDao::class.java.canonicalName,
            FmpLocalDao::class.java.canonicalName,
            FmpDatabase::class.java.canonicalName,
            FmpWebRequest::class.java.canonicalName,
            FmpRestRequest::class.java.canonicalName
        )
    }

    override fun getSupportedOptions(): Set<String?>? {
        //return Collections.singleton("org.gradle.annotation.processing.aggregating")
        return Collections.singleton("org.gradle.annotation.processing.isolating")
    }

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

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
        if (annotations.isNotEmpty()) {
            val startTime = System.currentTimeMillis()
            println("FmpProcessor started with annotations size = ${annotations.size}")
            val bindingDataList = mutableListOf<BindData>()
            // Create files for FmpDao annotation
            val fmpResult = collectAnnotationData(
                roundEnv.getElementsAnnotatedWith(FmpDao::class.java),
                bindingDataList,
                ::getDataFromFmpDao
            )
            // Create files for FmpLocalDao annotation
            val fmpLocalResult = collectAnnotationData(
                roundEnv.getElementsAnnotatedWith(FmpLocalDao::class.java),
                bindingDataList,
                ::getDataFromFmpLocalDao
            )

            // Web Requests
            val webElementRequests = roundEnv.getElementsAnnotatedWith(FmpWebRequest::class.java)
            processWebRequest(webElementRequests, bindingDataList)
            // Rest Requests
            val restElementRequests = roundEnv.getElementsAnnotatedWith(FmpRestRequest::class.java)
            processRestRequest(restElementRequests, bindingDataList)

            // If we has generated files for database without errors
            if (!fmpResult && !fmpLocalResult) {
                processDaos(bindingDataList)

                val databases = roundEnv.getElementsAnnotatedWith(FmpDatabase::class.java)
                databases?.forEach { currentDatabase ->
                    processDatabase(currentDatabase, bindingDataList)
                }
                allProperties.clear()
            }

            println("FmpProcessor finished in `${System.currentTimeMillis() - startTime}` ms")
        }
        return false
    }

    private fun processDatabase(databaseElement: Element, daoList: List<BindData>) {
        checkElementForRestrictions(FMP_DATABASE_NAME, databaseElement)

        val annotation = databaseElement.getAnnotation(FmpDatabase::class.java)
        val annotationType = databaseElement.asType()
        val databaseData = ClassName.bestGuess(annotationType.toString())

        val isSingleInstance = annotation.asSingleton
        val className = databaseData.simpleName
        val packName = databaseData.packageName
        val fileName = className.createFileName()

        val superClassName = ClassName(packName, className)
        val classTypeSpec = if (isSingleInstance) {
            TypeSpec.objectBuilder(fileName)
        } else {
            TypeSpec.classBuilder(fileName)
        }
        classTypeSpec.superclass(superClassName)

        // Extended classes only for Interfaces
        createDatabaseExtendedFunction(
            classTypeSpec,
            databaseElement,
            daoList,
            annotation.asDaoProvider
        )

        // than create function for database
        allProperties.addAll(
            createFunctions(classTypeSpec, databaseElement, daoList, annotation.asDaoProvider)
        )
        // create clearing function
        createClearProviders(classTypeSpec, allProperties)

        saveFiles(DATABASE_PACKAGE_NAME, fileName, listOf(classTypeSpec))
    }

    private fun createDatabaseExtendedFunction(
        classTypeSpec: TypeSpec.Builder,
        element: Element,
        daoList: List<BindData>,
        asProvider: Boolean
    ) {
        val extendedTypeMirrors = TYPE_UTILS.directSupertypes(element.asType())
        if (extendedTypeMirrors != null && extendedTypeMirrors.isNotEmpty()) {

            val extendedElements = extendedTypeMirrors.mapToInterfaceElements().filter { extElement ->
                !extElement.simpleName.toString().contains(FMP_BASE_NAME)
            }
            //println("----------------> createDatabaseExtendedFunction")
            //println("checkExtendedInterface $element - extendedElements ")
            extendedElements.forEach { extendedElement ->
                val funcPropNames = createFunctions(classTypeSpec, extendedElement, daoList, asProvider)
                allProperties.addAll(funcPropNames)
                createDatabaseExtendedFunction(classTypeSpec, extendedElement, daoList, asProvider)
            }
        }
    }

    private fun createFunctions(
        classTypeSpec: TypeSpec.Builder,
        element: Element,
        daoList: List<BindData>,
        asProvide: Boolean = false
    ): List<String> {
        val methods = element.enclosedElements.orEmpty()
        val allPropNames = mutableListOf<String>()
        //println("----------------> createFunctions")
        //println("------> element = $element | enclosedElements = $methods")

        methods.forEach { enclose ->
            if (enclose.isAbstractMethod()) {
                val (returnPack, returnClass) = enclose.asType().toString().getPackAndClass()
                val funcName = enclose.simpleName.toString()
                val returnedClass = ClassName(returnPack, returnClass)

                val returnElementData = daoList.find { it.mainData.className == returnClass }

                //println("------> returnedClass = $returnedClass | topLevel = $topLevel ")
                returnElementData?.let {
                    val instancePackName = if (returnElementData.isRequest) {
                        REQUEST_PACKAGE_NAME
                    } else {
                        DAO_PACKAGE_NAME
                    }
                    val returnedClassName = ClassName(instancePackName, it.fileName)

                    val instanceStatement = if (returnElementData.isRequest) {
                        buildString {
                            append("$TAG_CLASS_NAME(")
                            appendLine()
                            append("$FIELD_HYPER_HIVE = this.provideHyperHive(),")
                            appendLine()
                            append("$FIELD_DEFAULT_HEADERS = this.getDefaultHeaders()")
                            appendLine()
                            append(")")
                        }
                    } else {
                        "$TAG_CLASS_NAME($FIELD_PROVIDER = this)"
                    }
                    val funcSpec = FunSpec.builder(funcName)
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(returnedClass).apply {
                            if (asProvide) {
                                addStatement(
                                    "$RETURN_STATEMENT $instanceStatement",
                                    returnedClassName
                                )
                            } else {

                                val propName = funcName.createFileName()
                                allPropNames.add(propName)
                                val prop =
                                    PropertySpec.builder(
                                        propName,
                                        returnedClassName.copy(nullable = true)
                                    )
                                        .mutable()
                                        .addModifiers(KModifier.PRIVATE)
                                        .initializer(NULL_INITIALIZER)
                                        .build()

                                classTypeSpec.addProperty(prop)

                                val statementIf = "if($propName == $NULL_INITIALIZER) "
                                val statementCreate = "$propName = $instanceStatement"
                                val statementReturn = "$RETURN_STATEMENT $propName!!"
                                beginControlFlow(statementIf)
                                addStatement(statementCreate, returnedClassName)
                                endControlFlow()
                                addStatement(statementReturn)
                            }
                        }
                        .build()

                    classTypeSpec.addFunction(funcSpec)
                }
            }
        }


        return allPropNames
    }

    private fun createClearProviders(classTypeSpec: TypeSpec.Builder, allPropNames: List<String>) {
        if (allPropNames.isNotEmpty()) {

            val clearStatement = buildString {
                allPropNames.forEach { propName ->
                    append("$propName = $NULL_INITIALIZER")
                    appendLine()
                }
                append("super.clearProviders()")
            }

            val clearFuncSpec = FunSpec.builder("clearProviders")
                .addModifiers(KModifier.OVERRIDE)
                .addStatement(clearStatement)
                .build()

            classTypeSpec.addFunction(clearFuncSpec)
        }
    }

    private fun processDaos(moduleElements: List<BindData>) {
        moduleElements.filter { !it.isRequest }.forEach { bindData ->
            val classFileName = bindData.fileName
            val className = bindData.mainData.className
            val packageName = bindData.mainData.packName
            val mainClassName = ClassName(packageName, className)
            val classBuilders = mutableListOf<TypeSpec.Builder>()


            // generics type array for class type
            val genericsArray = mutableListOf<String>()

            val classTypeSpec =
                TypeSpec.classBuilder(classFileName)
                    .addSuperinterface(mainClassName)
                    .addProperties(createProperties())
            classBuilders.add(classTypeSpec)

            if (bindData.parameters.isNotEmpty()) {
                val localParams = bindData.parameters
                val requestFunc = createRequestFunction(localParams)
                /*val requestFuncAsync =
                    createRequestFunction(localParams, REQUEST_ASYNC_NAME, isAsync = true)*/
                classTypeSpec.addFunction(requestFunc.build())
                //classTypeSpec.addFunction(requestFuncAsync.build())
            }

            if (bindData.fields.isNotEmpty()) {
                val fileModelName = className.createFileName(MODEL_POSTFIX)
                val fileStatusName = className.createFileName(STATUS_POSTFIX)

                val modelClass = ClassName(DAO_PACKAGE_NAME, fileModelName)
                val statusClass = ClassName(DAO_PACKAGE_NAME, fileStatusName)
                val statusParentClaas = ClassName(MOBRUN_MODEL_PATH, MOBRUN_SELECTABLE_NAME)
                val modelTypeSpec = TypeSpec.classBuilder(fileModelName)
                val statusTypeSpec = TypeSpec.classBuilder(fileStatusName)
                    .superclass(statusParentClaas.parameterizedBy(modelClass))

                genericsArray.clear()
                genericsArray.add(modelClass.toString())
                genericsArray.add(statusClass.toString())

                val baseClassType = if (bindData.isLocal) {
                    IDao.IFmpLocalDao::class.asTypeName()
                } else {
                    IDao.IFmpDao::class.asTypeName()
                }

                val constructorSpec = FunSpec.constructorBuilder()
                val annotationJvmField = AnnotationSpec.builder(JvmField::class).build()
                val annotationPrimaryKey = AnnotationSpec.builder(PrimaryKey::class).build()

                bindData.fields.forEach { field ->
                    val data = field.asModelFieldData()
                    val annotationSerialize: AnnotationSpec = data.annotate.createSerializedAnnotation()

                    val currentType = data.type.asTypeName().copy(nullable = true)
                    val prop =
                        PropertySpec.builder(data.name, currentType)
                            .mutable(true)
                            .initializer(data.name)
                            .addAnnotation(annotationJvmField)
                            .apply {
                                if (data.isPrimaryKey) {
                                    addAnnotation(annotationPrimaryKey)
                                }
                            }
                            .addAnnotation(annotationSerialize)
                            .build()


                    constructorSpec.addParameter(
                        ParameterSpec.builder(data.name, currentType)
                            .defaultValue(NULL_INITIALIZER)
                            .build()
                    )
                    modelTypeSpec.addProperty(prop)
                }
                modelTypeSpec.primaryConstructor(constructorSpec.build())
                modelTypeSpec.addModifiers(KModifier.DATA)
                classBuilders.add(modelTypeSpec)
                classBuilders.add(statusTypeSpec)

                classTypeSpec.addSuperinterface(
                    baseClassType.parameterizedBy(modelClass, statusClass)
                )
            } else {
                val superInterfaces = TYPE_UTILS.directSupertypes(bindData.element.asType())
                //println("------> superInterfaces = $superInterfaces")
                genericsArray.clear()
                genericsArray.addAll(
                    superInterfaces
                        .map { it.toString() }
                        .firstOrNull { it.contains(FMP_DAO_NAME) || it.contains(FMP_LOCAL_DAO_NAME) }
                        .splitGenericsArray()
                )
            }

            //println("------> genericsArray = $genericsArray")
            // осноыной конструктор с дженериками
            classTypeSpec.primaryConstructor(constructorFunSpec(bindData, genericsArray))

            val functs = bindData.element.enclosedElements.orEmpty()
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
                        //println("-----> QUERY BUILD propName = $propName | propertyClass = $propertyClass")
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

            saveFiles(DAO_PACKAGE_NAME, classFileName, builders = classBuilders)
        }
    }


    private fun createRequestFunction(parameters: List<String>): FunSpec.Builder {
        val funcSpec = FunSpec.builder(REQUEST_NAME)
        var mapOfParams = TAG_MEMBER_HALF
        val paramsSize = parameters.size - 1
        val propertyClass = Any::class.asClassName()
        parameters.forEachIndexed { index, paramName ->
            val param = paramName.lowercase()
            val parameterSpec = ParameterSpec.builder(param, propertyClass).build()
            funcSpec.addParameter(parameterSpec)
            mapOfParams += "\"$paramName\" to $param"
            mapOfParams += if (index < paramsSize) ", " else ""
        }
        mapOfParams += ")"

        val replacedParams = FIELD_PARAMS_REPLACE.format(mapOfParams)
        val statement = "$RETURN_STATEMENT $FUNC_MEMBER_PARAMS_STATEMENT$replacedParams"
        val returnType = ClassName(MOBRUN_MODEL_PATH, MOBRUN_BASE_NAME)
        val updateFuncName = REQUEST_STATEMENT
        return funcSpec.addStatement(
            statement,
            MemberName(EXTENSIONS_PATH, updateFuncName),
            MemberName(KOTLIN_COLLECTION_PATH, KOTLIN_MAP_OF_NAME)
        ).returns(returnType)/*.apply {
            if (isAsync) {
                addModifiers(KModifier.SUSPEND)
            }
        }*/
    }

    private fun createProperties(): List<PropertySpec> {
        val hyperHiveProviderProp =
            PropertySpec.builder(FIELD_PROVIDER, IFmpDatabase::class)
                .initializer(FIELD_PROVIDER)
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .build()

        val resourceNameProp = PropertySpec.builder(FIELD_RESOURCE_NAME, String::class)
            .initializer(FIELD_RESOURCE_NAME)
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

        val isLocalProp = PropertySpec.builder(
            FIELD_DAO_FIELDS,
            DaoFieldsData::class.asTypeName().copy(nullable = true)
        ).mutable()
            .initializer(FIELD_DAO_FIELDS)
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .build()
        return listOf(
            hyperHiveProviderProp,
            resourceNameProp,
            parameterNameProp,
            isCachedProp,
            isLocalProp
        )

        /*if (bindData.isLocal || bindData.fields.isNotEmpty()) {

            } else {
                listOf(hyperHiveProviderProp, resourceNameProp, parameterNameProp, isCachedProp)
            }*/
    }

    // создаем иницилизируешие поля для конструктора а так жу функцию init {  }
    private fun constructorFunSpec(bindData: BindData, superTypeGenerics: List<String>): FunSpec {
        val resourceName = ParameterSpec.builder(FIELD_RESOURCE_NAME, String::class).apply {
            if (bindData.resourceName.isNotEmpty()) {
                defaultValue("\"${bindData.resourceName}\"")
            }
        }.build()

        val parameterName = ParameterSpec.builder(FIELD_TABLE, String::class)
            .defaultValue("\"${bindData.tableName}\"")
            .build()

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
                val isLocalProp = ParameterSpec.builder(
                    FIELD_DAO_FIELDS,
                    DaoFieldsData::class.asTypeName().copy(nullable = true)
                )
                    .defaultValue(NULL_INITIALIZER)
                    .build()
                addParameter(isLocalProp)

                //println("--------> superTypeGenerics = $superTypeGenerics")

                if (bindData.createTableOnInit) {
                    val members = mutableListOf<Any>()
                    members.add(MemberName(EXTENSIONS_PATH, INIT_CREATE_TABLE))
                    val initStatement = buildString {
                        if (superTypeGenerics.isNotEmpty()) {
                            append(FUNC_MEMBER_STATEMENT_GENERIC)
                            //val genSize = superTypeGenerics.size - 1
                            val className = superTypeGenerics.firstOrNull().orEmpty()
                            append(className)
                            /*superTypeGenerics.forEachIndexed { index, className ->
                                append(className)
                                if (index < genSize) {
                                    append(DELIMETER)
                                }
                            }*/
                            append(FUNC_MEMBER_STATEMENT_GENERIC_CLOSE)
                        } else {
                            append(FUNC_MEMBER_STATEMENT)
                        }
                    }

                    addStatement(
                        initStatement,
                        MemberName(EXTENSIONS_PATH, INIT_CREATE_TABLE)
                    )
                }
            }
            .build()
    }

    private fun getDataFromFmpDao(element: Element): BindData {
        val jClass = FmpDao::class.java
        val annotation = element.getAnnotation(jClass)
        return createAnnotationData(
            annotationName = jClass.simpleName,
            element = element,
            resourceName = annotation.resourceName,
            tableName = annotation.tableName,
            isDelta = annotation.isDelta,
            parameters = annotation.parameters,
            fields = annotation.fields,
            isLocal = false,
            createTableOnInit = false
        )
    }

    private fun getDataFromFmpLocalDao(element: Element): BindData {
        val jClass = FmpLocalDao::class.java
        val annotation = element.getAnnotation(jClass)
        return createAnnotationData(
            annotationName = jClass.simpleName,
            element = element,
            resourceName = annotation.resourceName,
            tableName = annotation.tableName,
            parameters = emptyArray(),
            fields = annotation.fields,
            createTableOnInit = annotation.createTableOnInit,
            isLocal = true
        )
    }

    private fun createAnnotationData(
        annotationName: String,
        element: Element,
        resourceName: String,
        tableName: String,
        parameters: Array<String>,
        fields: Array<String>,
        createTableOnInit: Boolean,
        isDelta: Boolean = false,
        isLocal: Boolean = false
    ): BindData {

        if (fields.isNotEmpty()) {
            checkElementForRestrictions(element = element, annotationName = FMP_FIELDS_DAO_NAME)
        } else {
            checkElementForRestrictions(element = element, annotationName = annotationName)
        }

        val annotationType = element.asType()
        val elementClassName = ClassName.bestGuess(annotationType.toString())
        val className = elementClassName.simpleName
        val fileName = className.createFileName()

        return BindData(
            element = element,
            fileName = fileName,
            createTableOnInit = createTableOnInit,
            parameters = parameters.toList(),
            fields = fields.toList(),
            mainData = TypeData(
                packName = elementClassName.packageName,
                className = elementClassName.simpleName
            ),
            resourceName = resourceName,
            tableName = tableName,
            isDelta = isDelta,
            isLocal = isLocal,
            isRequest = false
        )
    }

    private fun checkElementForRestrictions(
        annotationName: String,
        element: Element
    ) {
        val realAnnotationName = element.annotationMirrors.firstOrNull()?.annotationType.toString()
        val annotationType = element.asType()

        val hasKindError = if (annotationName == FMP_DATABASE_ANNOTATION_NAME) {
            element.kind == ElementKind.INTERFACE
        } else {
            element.kind == ElementKind.CLASS
        }

        val hasAbstractError = hasKindError || !element.modifiers.contains(Modifier.ABSTRACT)
        if (hasAbstractError) {
            throw IllegalStateException(
                "$annotationType with $realAnnotationName annotation should be an interface and " +
                        "implement $annotationName to be correctly processed"
            )
        }

        if (!checkExtendedInterface(element, annotationName)) {
            throw IllegalStateException(
                "$annotationType with $realAnnotationName annotation should implement pro.krit.hhivecore.IDao.I${annotationName}" +
                        " or have annotation parameter 'fields' to be correctly processed"
            )
        }
    }

    private fun checkExtendedInterface(element: Element, comparatorName: String): Boolean {
        var hasElement = false
        val extendedTypeMirrors = TYPE_UTILS.directSupertypes(element.asType())
        val extendedElements = extendedTypeMirrors.mapToInterfaceElements()
        if (!extendedElements.isNullOrEmpty()) {
            extendedElements.forEach {
                hasElement = it.simpleName.toString().contains(comparatorName)
                if (!hasElement) {
                    hasElement = checkExtendedInterface(it, comparatorName)
                } else {
                    return hasElement
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
                !currentClassName.contains(BASE_FMP_DATABASE_NAME)
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


    ///////----------- REQUESTS
    private fun processWebRequest(elementsSet: Set<Element>, daoListMap: MutableList<BindData>) {
        val requestInterface = IRequest.IWebRequest::class.asTypeName()
        elementsSet.forEach { element ->
            val annotation = element.getAnnotation(FmpWebRequest::class.java)
            val resourceName = annotation.resourceName
            val parameters = annotation.parameters.toList()
            val bindData = processRequest(element, parameters, requestInterface, resourceName)
            bindData?.let {
                daoListMap.add(it)
            }
        }
    }

    private fun processRestRequest(elementsSet: Set<Element>, daoListMap: MutableList<BindData>) {
        val requestInterface = IRequest.IRestRequest::class.asTypeName()
        elementsSet.forEach { element ->
            val annotation = element.getAnnotation(FmpRestRequest::class.java)
            val resourceName = annotation.resourceName
            val parameters = annotation.parameters.toList()
            val bindData = processRequest(element, parameters, requestInterface, resourceName)
            bindData?.let {
                daoListMap.add(it)
            }
        }
    }


    private fun processRequest(
        element: Element,
        parameters: List<String>,
        requestInterface: ClassName,
        resourceName: String
    ): BindData? {
        val kind = element.kind
        val annotation = element.annotationMirrors.firstOrNull()

        if (resourceName.isEmpty()) {
            throw IllegalStateException("'$element' can not have empty 'resourceName' property for annotation $annotation")
        }

        val isErrorType =
            (kind == ElementKind.METHOD || kind == ElementKind.CONSTRUCTOR || kind == ElementKind.CLASS)
        if (isErrorType && kind != ElementKind.INTERFACE) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                "Only interfaces can be annotated as '@FmpRestRequest' or '@FmpWebRequest' "
            )
            return null
        }

        val elementEnclosed = element.enclosedElements?.filter {
            it.kind != ElementKind.CLASS && it.kind != ElementKind.CONSTRUCTOR
        }.orEmpty()

        // main class implementation
        val (mainPackName, mainClassName) = element.asType().toString().getPackAndClass()
        val typeClassName = mainClassName.createFileName()

        //------ ALL ELEMENT CLASSES
        val elementsFiles = mutableListOf<TypeSpec.Builder>()

        //------ Main Request Class
        val mainSuperInterface = ClassName(mainPackName, mainClassName)
        val mainClassTypeSpec = TypeSpec.classBuilder(typeClassName)
            .addSuperinterface(mainSuperInterface)

        val mainClassPropSpec = FunSpec.constructorBuilder()
        createMainRequestClass(
            resourceName,
            //mainClassName,
            parameters,
            mainClassPropSpec,
            mainClassTypeSpec
        )
        elementsFiles.add(mainClassTypeSpec)

        // типы параметров запроса
        val paramsAnnotationClasses = mutableMapOf<String, TypeName>()

        if (elementEnclosed.isNotEmpty()) {
            //------ Result models
            val resultModelClassName = ClassName(
                REQUEST_PACKAGE_NAME,
                mainClassName.createFileName(RESULT_MODEL_POSTFIX)
            )
            val resultModelTypeSpec =
                TypeSpec.classBuilder(resultModelClassName)
            resultModelTypeSpec.addModifiers(KModifier.DATA)
            val resultConstructorSpec = FunSpec.constructorBuilder()

            //------- Result Model Fields
            elementEnclosed.forEach { inter ->
                //println("-------> elementEnclosed = ${inter} | inter.kind = ${inter.kind}")
                val tableAnnotation = inter.getAnnotation(FmpTable::class.java)
                val paramAnnotation = inter.getAnnotation(FmpParam::class.java)

                val annotationName = tableAnnotation?.name ?: paramAnnotation?.name.orEmpty()

                if (inter.kind != ElementKind.INTERFACE) {
                    throw IllegalStateException("You can use '@FmpTable' or '@FmpParam' annotation only with interfaces")
                }

                val isTableAnnotation = tableAnnotation != null
                val isParamAnnotation = paramAnnotation != null

                val annotationInnerName = tableAnnotation?.name ?: paramAnnotation?.name.orEmpty()
                if (annotationInnerName.isEmpty()) {
                    throw IllegalStateException("Please specify annotation property 'name'. It can not be empty")
                }

                val propClassFields = tableAnnotation?.fields ?: paramAnnotation?.fields.orEmpty()
                if (propClassFields.isEmpty()) {
                    throw IllegalStateException("You cannot use '@FmpTable' or '@FmpParam' annotation with empty 'fields' property")
                }

                // название таблицы
                val postFixName = if (isTableAnnotation) MODEL_POSTFIX else PARAMS_POSTFIX
                val propName = mainClassName + inter.simpleName.toString().capitalizeFirst()
                val propModelData = annotationInnerName.asModelFieldData()
                val propTypeClassName = propName.createFileName(postFixName).capitalizeFirst()
                val propClassName = ClassName(REQUEST_PACKAGE_NAME, propTypeClassName)
                val propClassSpec = TypeSpec.classBuilder(propTypeClassName)
                    .addModifiers(KModifier.DATA)

                // добавляем в маппу для параметров запроса
                if (isParamAnnotation) {
                    val isList = paramAnnotation.isList
                    // выбираем правильный тип данных
                    val typeName = createListTypeName(propClassName, isList)
                    paramsAnnotationClasses[annotationInnerName] = typeName
                }

                // table constructor properties
                val constructorPropSpec = FunSpec.constructorBuilder()
                // does table have enumeric parameter in annotation
                val isNumericModel =
                    tableAnnotation?.isNumeric ?: paramAnnotation?.isNumeric ?: false
                propClassFields.forEachIndexed { index, name ->
                    addTableModelProperty(
                        name,
                        constructorPropSpec,
                        propClassSpec,
                        null,
                        isNumericModel,
                        index
                    )
                }
                propClassSpec.primaryConstructor(constructorPropSpec.build()).apply {
                    if (isNumericModel) {
                        /* val (numPackName, numClassName) = NumeratedFields::class.java.toString().getPackAndClass()
                         val numerateClassName = ClassName(numPackName, numClassName)*/
                        addAnnotation(createCountFieldsAnnotation(propClassFields.size))
                        if (isTableAnnotation) {
                            addSuperinterface(NumeratedFields::class.java)
                        } else {
                            addSuperinterface(CustomParameter::class.java)
                            val returnMapStatement = buildString {
                                append("$RETURN_STATEMENT \"$annotationName\"")
                            }
                            val stringTypeName = String::class.asTypeName()
                            val getParamsMapFunSpec = FunSpec.builder(FUNC_GET_PARAMETER_NAME)
                                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                                .addStatement(returnMapStatement)
                                .returns(stringTypeName)
                                .build()

                            addFunction(getParamsMapFunSpec)
                        }
                    }
                }
                elementsFiles.add(propClassSpec)

                if (isTableAnnotation) {
                    // result model property
                    createResultModelProperty(
                        propModelData.name,
                        tableAnnotation.name,
                        propClassName,
                        resultConstructorSpec,
                        resultModelTypeSpec,
                        tableAnnotation.isList
                    )
                }
            }

            //------ Request Params Class
            if (parameters.isNotEmpty()) {
                val paramsClass =
                    createRequestParams(mainClassName, parameters, paramsAnnotationClasses)
                elementsFiles.add(1, paramsClass)
            }

            // add result model class
            resultModelTypeSpec.primaryConstructor(resultConstructorSpec.build())
            elementsFiles.add(resultModelTypeSpec)

            // Raw Model Class
            /*val rawModelClassName = ClassName(
                REQUEST_PACKAGE_NAME,
                mainClassName.createFileName(RAW_MODEL_POSTFIX)
            )
            val rawModelTypeSpec = createRawModelClass(rawModelClassName, resultModelClassName)
            elementsFiles.add(rawModelTypeSpec)*/

            // Raw Status Class
            val respondStatusClassName = ClassName(
                REQUEST_PACKAGE_NAME,
                mainClassName.createFileName(RESPOND_STATUS_POSTFIX)
            )
            val rawStatusTypeSpec =
                createRawStatusClass(respondStatusClassName, resultModelClassName)
            elementsFiles.add(rawStatusTypeSpec)

            val parametrizedInterface =
                requestInterface.parameterizedBy(resultModelClassName, respondStatusClassName)
            mainClassTypeSpec.addSuperinterface(parametrizedInterface)

        } /*else {
            val message =
                java.lang.String.format(" Please set private inner interfaces with annotation '@FmpTable' for request '$element'. ")
            messager.printMessage(Diagnostic.Kind.WARNING, message)
        }*/


        // save all classes in one file
        saveFiles(REQUEST_PACKAGE_NAME, typeClassName, elementsFiles)

        return BindData(
            element = element,
            fileName = typeClassName,
            createTableOnInit = false,
            parameters = emptyList(),
            fields = emptyList(),
            mainData = TypeData(
                packName = mainPackName,
                className = mainClassName
            ),
            resourceName = resourceName,
            tableName = resourceName,
            isDelta = false,
            isRequest = true,
            isLocal = true
        )
    }

    private fun createRawStatusClass(
        respondStatusClassName: ClassName,
        rawModelClassName: ClassName
    ): TypeSpec.Builder {
        val respondStatusTypeSpec = TypeSpec.classBuilder(respondStatusClassName)
        val baseRespondStatusClassType =
            ObjectRawStatus::class.asTypeName().parameterizedBy(rawModelClassName)
        return respondStatusTypeSpec.superclass(baseRespondStatusClassType)
    }

    /*private fun createRawModelClass(
        rawModelClassName: ClassName,
        resultModelClassName: ClassName
    ): TypeSpec.Builder {
        val rawModelTypeSpec = TypeSpec.classBuilder(rawModelClassName)
        val baseRawModelClassType =
            BaseFmpRawModel::class.asTypeName().parameterizedBy(resultModelClassName)
        return rawModelTypeSpec.superclass(baseRawModelClassType)
    }*/

    private fun createResultModelProperty(
        name: String,
        annotationSerializedName: String,
        parameterClassName: ClassName,
        constructorSpec: FunSpec.Builder,
        classTypeSpec: TypeSpec.Builder,
        fieldReturnList: Boolean = false
    ) {
        val annotationSerialize = annotationSerializedName.createSerializedAnnotation()
        val returnClassName = createListTypeName(parameterClassName, fieldReturnList)
        val propBuilder = PropertySpec.builder(name, returnClassName, KModifier.PUBLIC)
            .initializer(name)
            .addAnnotation(annotationSerialize)
            .build()

        val paramBuilder =
            ParameterSpec.builder(name, returnClassName)
                .defaultValue(NULL_INITIALIZER)
                .build()

        constructorSpec.addParameter(paramBuilder)
        classTypeSpec.addProperty(propBuilder)
    }

    private fun createListTypeName(
        originalClassName: ClassName,
        fieldReturnList: Boolean
    ): TypeName {
        return if (fieldReturnList) {
            val list = ClassName(KOTLIN_COLLECTION_PATH, KOTLIN_LIST_NAME)
            list.parameterizedBy(originalClassName).copy(nullable = true)
        } else {
            originalClassName.copy(nullable = true)
        }
    }

    private fun addTableModelProperty(
        name: String,
        constructorSpec: FunSpec.Builder,
        classTypeSpec: TypeSpec.Builder,
        modelTypeName: TypeName? = null,
        isNumericModel: Boolean = false,
        numIndex: Int = -1
    ) {

        val modelData = name.asModelFieldData()
        val propName = modelData.name

        val annotationSerialize = modelData.annotate.createSerializedAnnotation()
        val type = modelTypeName ?: modelData.type.asTypeName().copy(nullable = true)
        val propSpec =
            PropertySpec.builder(propName, type, KModifier.PUBLIC)
                .initializer(propName)
                .mutable(true)
                .addAnnotation(annotationSerialize).apply {
                    if (isNumericModel && numIndex >= 0) {
                        addAnnotation(createJavaFieldAnnotation())
                        addAnnotation(createParameterFieldAnnotation(numIndex))
                    }
                }
                .build()

        val paramSpec =
            ParameterSpec.builder(propName, type)
                .defaultValue(NULL_INITIALIZER)
                .build()
        constructorSpec.addParameter(paramSpec)
        classTypeSpec.addProperty(propSpec)
    }

    private fun createMainRequestClass(
        resourceName: String,
        //mainClassName: String,
        parameters: List<String>,
        constructorSpec: FunSpec.Builder,
        classTypeSpec: TypeSpec.Builder
    ) {
        val classType = HyperHive::class.asTypeName()
        val hyperHivePropName = FIELD_HYPER_HIVE
        val propHyperSpec =
            PropertySpec.builder(hyperHivePropName, classType, KModifier.PUBLIC, KModifier.OVERRIDE)
                .initializer(hyperHivePropName)
                .build()
        val paramHyperSpec = ParameterSpec.builder(hyperHivePropName, classType)
            .build()

        //val anyTypeName = Any::class.asTypeName()
        val stringTypeName = String::class.asTypeName()
        val mapTypeName = Map::class.asTypeName().parameterizedBy(stringTypeName, stringTypeName)
        val nullableMapType = mapTypeName.copy(nullable = true)
        val propHeadersSpec = PropertySpec.builder(
            FIELD_DEFAULT_HEADERS,
            nullableMapType,
            KModifier.PUBLIC,
            KModifier.OVERRIDE
        )
            .mutable()
            .initializer(FIELD_DEFAULT_HEADERS)
            .build()

        //val anyTypeNameNullable = anyTypeName.copy(nullable = true)

        val paramHeadersSpec = ParameterSpec.builder(FIELD_DEFAULT_HEADERS, nullableMapType)
            .defaultValue(NULL_INITIALIZER)
            .build()

        val propResourceSpec = PropertySpec.builder(
            FIELD_RESOURCE_NAME,
            stringTypeName,
            KModifier.PUBLIC,
            KModifier.OVERRIDE
        )
            .initializer(FIELD_RESOURCE_NAME)
            .build()

        val paramResourceSpec = ParameterSpec.builder(FIELD_RESOURCE_NAME, stringTypeName)
            .defaultValue("\"${resourceName}\"")
            .build()

        val fullParamsSize = parameters.size
        val mapMemberName = MemberName(KOTLIN_COLLECTION_PATH, KOTLIN_MAP_OF_NAME)
        var commentOfParams = PARAMS_COMMENT_FIRST
        val returnMapStatement = buildString {
            if (parameters.isEmpty()) {
                commentOfParams += PARAMS_COMMENT_SECOND
                append("$RETURN_STATEMENT $TAG_MEMBER_FULL")
            } else {
                commentOfParams += PARAMS_COMMENT_THIRD.format("$fullParamsSize")

                val paramSize = fullParamsSize - 1
                parameters.forEachIndexed { index, param ->
                    commentOfParams += "'$param'"
                    if (index < paramSize) {
                        commentOfParams += ", "
                    }
                }

                append(createValueParamsStatement(parameters))
                val returnedMap = createMapStatement(parameters)
                append("$RETURN_STATEMENT $returnedMap")
            }
        }

/*
        val paramsClassName = ClassName(
            REQUEST_PACKAGE_NAME,
            mainClassName.createFileName(PARAMS_POSTFIX)
        )

        val returnClassStatement = buildString {
            append("$RETURN_STATEMENT $NULL_INITIALIZER")
            if (parameters.isEmpty()) {
                append("$RETURN_STATEMENT $NULL_INITIALIZER")
            } else {
                append("$RETURN_STATEMENT $TAG_CLASS_NAME(")
                val paramSize = parameters.size - 1
                parameters.forEachIndexed { index, param ->
                    val paramModel = param.asModelFieldData()
                    val paramName = paramModel.name
                    val fieldGet = FIELD_PARAMS_GET_NULL.format("$index")
                    append("$paramName = $fieldGet as? Any")
                    if (index < paramSize) {
                        append(", ")
                    }
                }
                append(")")
            }
        }

       val createParamsFunSpec = FunSpec.builder(FUNC_CREATE_PARAMS_NAME)
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .addParameter(FIELD_PARAMS, anyTypeName, KModifier.VARARG)
            .addStatement(returnClassStatement, paramsClassName)
            .returns(anyTypeNameNullable)
            .addKdoc(commentOfParams)
            .build()
            */

        val createParamsMapFunSpec = FunSpec.builder(FUNC_CREATE_PARAMS_MAP_NAME)
            .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
            .addParameter(FIELD_PARAMS, stringTypeName, KModifier.VARARG)
            .addStatement(returnMapStatement, mapMemberName)
            .returns(mapTypeName)
            .addKdoc(commentOfParams)
            .build()

        constructorSpec.addParameter(paramHyperSpec)
        constructorSpec.addParameter(paramHeadersSpec)
        constructorSpec.addParameter(paramResourceSpec)

        classTypeSpec.addProperty(propHyperSpec)
        classTypeSpec.addProperty(propHeadersSpec)
        classTypeSpec.addProperty(propResourceSpec)

        classTypeSpec.primaryConstructor(constructorSpec.build())
        classTypeSpec.addFunction(createParamsMapFunSpec)
        //classTypeSpec.addFunction(createParamsFunSpec)
    }

    private fun createValueParamsStatement(parameters: List<String>): String {
        return buildString {
            parameters.forEachIndexed { index, _ ->
                val fieldGet = FIELD_PARAMS_GET.format("$index")
                append("val param${index + 1}: String = $fieldGet")
                appendLine()
            }
        }
    }

    private fun createMapStatement(
        parameters: List<String>,
        needLowerCase: Boolean = false
    ): String {
        return buildString {
            append(TAG_MEMBER_HALF)
            val paramSize = parameters.size - 1
            parameters.forEachIndexed { index, param ->
                val paramName = if (needLowerCase) {
                    param.asModelFieldData().name
                } else {
                    param
                }
                append("\"$paramName\" to param${index + 1}")
                if (index < paramSize) {
                    append(", ")
                }
            }
            append(")")
        }
    }

    private fun createRequestParams(
        mainClassName: String,
        properties: List<String>,
        annotationParams: Map<String, TypeName> = emptyMap()
    ): TypeSpec.Builder {
        val paramsClassName = mainClassName.createFileName(PARAMS_POSTFIX)
        val paramsClassSpec = TypeSpec.classBuilder(paramsClassName)
            .addModifiers(KModifier.DATA)

        val constructorPropSpec = FunSpec.constructorBuilder()
        properties.forEach {
            val modelTypeName: TypeName? = annotationParams[it]
            addTableModelProperty(it, constructorPropSpec, paramsClassSpec, modelTypeName)
        }
        paramsClassSpec.primaryConstructor(constructorPropSpec.build())
        return paramsClassSpec
    }

    private fun saveFiles(
        packageName: String,
        classFileName: String,
        builders: List<TypeSpec.Builder>
    ) {
        val file = FileSpec.builder(packageName, classFileName)
            .addFileComment(FILE_COMMENT)
            .apply {
                builders.forEach { builder ->
                    addType(builder.build())
                }
            }
            .build()
        try {
            file.writeTo(filer)
        } catch (e: IOException) {
            val message = java.lang.String.format("Unable to write file: %s", e.message)
            messager.printMessage(Diagnostic.Kind.ERROR, message)
        }
    }
}