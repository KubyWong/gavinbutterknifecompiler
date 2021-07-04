package com.example.gavin_butterknife_processor;

import com.example.gavin_butterknife_annotation.Constant;
import com.example.gavin_butterknife_annotation.GavinBindView;
import com.example.gavin_butterknife_annotation.GavinViewBinder;
import com.example.gavin_butterknife_annotation.SimpleUtils;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;


@SupportedAnnotationTypes("com.example.gavin_butterknife_annotation.GavinBindView")//设置支持的注解类型
@SupportedSourceVersion(SourceVersion.RELEASE_8)//设置支持的版本
@AutoService(Processor.class)
public class GavinButterknifeProcessor extends AbstractProcessor {
    // 操作Element工具类 (类、函数、属性都是Element)
    private Elements elementUtils;

    // type(类信息)工具类，包含用于操作TypeMirror的工具方法
    private Types typeUtils;

    // Messager用来报告错误，警告和其他提示信息
    private Messager messager;

    // 文件生成器 类/资源，Filter用来创建新的类文件，class文件以及辅助文件
    private Filer filer;

    // key:类节点, value:被@BindView注解的属性集合
    private Map<TypeElement, List<VariableElement>> tempBindViewMap = new HashMap<>();

    // 该方法主要用于一些初始化的操作，通过该方法的参数ProcessingEnvironment可以获取一些列有用的工具类
    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        // 初始化
        elementUtils = processingEnvironment.getElementUtils();
        typeUtils = processingEnvironment.getTypeUtils();
        messager = processingEnvironment.getMessager();
        filer = processingEnvironment.getFiler();
        messager.printMessage(Diagnostic.Kind.NOTE,
                "注解处理器初始化完成，开始处理注解------------------------------->");
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        System.out.println("GavinButterknifeProcessor.process...");

        // 一旦有属性上使用@BindView注解
        if (!SimpleUtils.isEmpty(set)) {
            // 获取所有被 @BindView 注解的 元素集合
            Set<? extends Element> bindViewElements = roundEnvironment.getElementsAnnotatedWith(GavinBindView.class);
            elementsInGroup(bindViewElements);
            try {
                createJavaFile();
            } catch (IOException e) {
                e.printStackTrace();
                messager.printMessage(Diagnostic.Kind.NOTE, "@IOException >>> " + e.getMessage());
            }
        }

        return false;
    }

    /**
     * 分类，key为类名，value为注解的Element集合
     * {"Sample1Activity":[textview1Element,textview2Element]
     * ,"Sample2Activity":[textview1Element,textview2Element]}
     *
     * @param bindViewElements
     */
    private void elementsInGroup(Set<? extends Element> bindViewElements) {
        if (!SimpleUtils.isEmpty(bindViewElements)) {
            for (Element element : bindViewElements) {
                messager.printMessage(Diagnostic.Kind.NOTE, "@BindView >>> " + element.getSimpleName());
                if (element.getKind() == ElementKind.FIELD) {
                    VariableElement fieldElement = (VariableElement) element;
                    // 注解在属性之上，属性节点父节点是类节点
                    TypeElement enclosingElement = (TypeElement) fieldElement.getEnclosingElement();
                    // 如果map集合中的key：类节点存在，直接添加属性
                    if (tempBindViewMap.containsKey(enclosingElement)) {
                        tempBindViewMap.get(enclosingElement).add(fieldElement);
                    } else {
                        List<VariableElement> fields = new ArrayList<>();
                        fields.add(fieldElement);
                        tempBindViewMap.put(enclosingElement, fields);
                    }
                }
            }
        }
    }

    void createJavaFile() throws IOException {
        TypeElement viewBinderType = elementUtils.getTypeElement(GavinViewBinder.class.getName());

        for (Map.Entry<TypeElement, List<VariableElement>> entry :
                tempBindViewMap.entrySet()) {
            //类名 TestActivity
            ClassName className = ClassName.get(entry.getKey());

            //实现接口泛型 implements GavinViewBinder<TestActivity>
            ParameterizedTypeName typeName = ParameterizedTypeName
                    .get(ClassName.get(viewBinderType), className);

            //参数体配置(final TestActivity target)
            ParameterSpec parameterSpec = ParameterSpec.builder(className,
                    Constant.CODE_PARAM)
                    .addModifiers(Modifier.FINAL)
                    .build();

            //声明方法
            MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(Constant.CODE_METHOD_NAME)
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(parameterSpec);

            for (Element fieldElement :
                    entry.getValue()) {
                //获取属性名
                String fieldName = fieldElement.getSimpleName().toString();
                //获取@GavinBindView注解的值
                int annotationValue = fieldElement.getAnnotation(GavinBindView.class).value();
                messager.printMessage(Diagnostic.Kind.NOTE, fieldName + ".annotationValue >>> " + annotationValue);
                //target.tv = target.findViewById(R.id.tv);
                String methodContent = "$N." + fieldName + " = $N.findViewById($L)";
                methodBuilder.addStatement(methodContent,
                        Constant.CODE_PARAM,
                        Constant.CODE_PARAM,
                        annotationValue);

                messager.printMessage(Diagnostic.Kind.NOTE, methodBuilder.toString());
            }

            //生成类
            TypeSpec typeSpec = TypeSpec.classBuilder(className.simpleName() + Constant.CODE_CLASS_EXT_NAME)//class XXX$ViewBinder
                    .addSuperinterface(typeName)//implements GavinViewBinder<XXXActivity>
                    .addModifiers(Modifier.PUBLIC)//public
                    .addMethod(methodBuilder.build())// 加入前面的方法
                    .build();

            //生成文件
            JavaFile javaFile = JavaFile.builder(className.packageName(), typeSpec)
                    .build();
            javaFile.writeTo(filer);
        }
    }
}

