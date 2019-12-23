package com.power.doc.helper;

import com.google.gson.Gson;
import com.power.common.util.JsonFormatUtil;
import com.power.common.util.StringUtil;
import com.power.doc.builder.ProjectDocConfigBuilder;
import com.power.doc.constants.DocAnnotationConstants;
import com.power.doc.constants.DocGlobalConstants;
import com.power.doc.constants.DocTags;
import com.power.doc.model.ApiMethodDoc;
import com.power.doc.model.ApiReturn;
import com.power.doc.model.CustomRespField;
import com.power.doc.model.postman.request.body.FormData;
import com.power.doc.utils.*;
import com.thoughtworks.qdox.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yu 2019/12/21.
 */
public class JsonBuildHelper {

    public static final String JSON_REQUEST_BODY = "requestBody";
    public static final String JSON_GET_PARAMS = "urlParams";
    /**
     * build return json
     *
     * @param method The JavaMethod object
     * @return String
     */
    public static String buildReturnJson(JavaMethod method, ProjectDocConfigBuilder builder) {
        if ("void".equals(method.getReturnType().getFullyQualifiedName())) {
            return "This api return nothing.";
        }
        ApiReturn apiReturn = DocClassUtil.processReturnType(method.getReturnType().getGenericCanonicalName());
        String returnType = apiReturn.getGenericCanonicalName();
        String typeName = apiReturn.getSimpleName();
        return JsonFormatUtil.formatJson(buildJson(typeName, returnType, true, 0, new HashMap<>(),builder));
    }

    /**
     * @param typeName             type name
     * @param genericCanonicalName genericCanonicalName
     * @param isResp               Response flag
     * @param counter              Recursive counter
     * @return String
     */
    public static String buildJson(String typeName, String genericCanonicalName,
                                   boolean isResp, int counter, Map<String, String> registryClasses, ProjectDocConfigBuilder builder) {
        if (registryClasses.containsKey(typeName) && counter > registryClasses.size()) {
            return "{\"$ref\":\"...\"}";
        }
        registryClasses.put(typeName, typeName);
        if (DocClassUtil.isMvcIgnoreParams(typeName)) {
            if (DocGlobalConstants.MODE_AND_VIEW_FULLY.equals(typeName)) {
                return "Forward or redirect to a page view.";
            } else {
                return "Error restful return.";
            }
        }
        if (DocClassUtil.isPrimitive(typeName)) {
            return StringUtil.removeQuotes(DocUtil.jsonValueByType(typeName));
        }
        StringBuilder data0 = new StringBuilder();
        JavaClass cls = builder.getClassByName(typeName);
        data0.append("{");
        String[] globGicName = DocClassUtil.getSimpleGicName(genericCanonicalName);
        StringBuilder data = new StringBuilder();
        if (DocClassUtil.isCollection(typeName) || DocClassUtil.isArray(typeName)) {
            data.append("[");
            if (globGicName.length == 0) {
                data.append("{\"object\":\"any object\"}");
                data.append("]");
                return data.toString();
            }
            String gNameTemp = globGicName[0];
            String gName = DocClassUtil.isArray(typeName) ? gNameTemp.substring(0, gNameTemp.indexOf("[")) : globGicName[0];
            if (DocGlobalConstants.JAVA_OBJECT_FULLY.equals(gName)) {
                data.append("{\"waring\":\"You may use java.util.Object instead of display generics in the List\"}");
            } else if (DocClassUtil.isPrimitive(gName)) {
                data.append(DocUtil.jsonValueByType(gName)).append(",");
                data.append(DocUtil.jsonValueByType(gName));
            } else if (gName.contains("<")) {
                String simple = DocClassUtil.getSimpleName(gName);
                String json = buildJson(simple, gName,  isResp, counter + 1, registryClasses,builder);
                data.append(json);
            } else if (DocClassUtil.isCollection(gName)) {
                data.append("\"any object\"");
            } else {
                String json = buildJson(gName, gName,  isResp, counter + 1, registryClasses,builder);
                data.append(json);
            }
            data.append("]");
            return data.toString();
        } else if (DocClassUtil.isMap(typeName)) {
            String gNameTemp = genericCanonicalName;
            String[] getKeyValType = DocClassUtil.getMapKeyValueType(gNameTemp);
            if (getKeyValType.length == 0) {
                data.append("{\"mapKey\":{}}");
                return data.toString();
            }
            if (!DocGlobalConstants.JAVA_STRING_FULLY.equals(getKeyValType[0])) {
                throw new RuntimeException("Map's key can only use String for json,but you use " + getKeyValType[0]);
            }
            String gicName = gNameTemp.substring(gNameTemp.indexOf(",") + 1, gNameTemp.lastIndexOf(">"));
            if (DocGlobalConstants.JAVA_OBJECT_FULLY.equals(gicName)) {
                data.append("{").append("\"mapKey\":").append("{\"waring\":\"You may use java.util.Object for Map value; smart-doc can't be handle.\"}").append("}");
            } else if (DocClassUtil.isPrimitive(gicName)) {
                data.append("{").append("\"mapKey1\":").append(DocUtil.jsonValueByType(gicName)).append(",");
                data.append("\"mapKey2\":").append(DocUtil.jsonValueByType(gicName)).append("}");
            } else if (gicName.contains("<")) {
                String simple = DocClassUtil.getSimpleName(gicName);
                String json = buildJson(simple, gicName,  isResp, counter + 1, registryClasses,builder);
                data.append("{").append("\"mapKey\":").append(json).append("}");
            } else {
                data.append("{").append("\"mapKey\":").append(buildJson(gicName, gNameTemp, isResp, counter + 1, registryClasses,builder)).append("}");
            }
            return data.toString();
        } else if (DocGlobalConstants.JAVA_OBJECT_FULLY.equals(typeName)) {
            if (DocGlobalConstants.JAVA_OBJECT_FULLY.equals(typeName)) {
                data.append("{\"object\":\" any object\"},");
                // throw new RuntimeException("Please do not return java.lang.Object directly in api interface.");
            }
        } else {
            List<JavaField> fields = JavaClassUtil.getFields(cls, 0);
            boolean isGenerics = JavaFieldUtil.checkGenerics(fields);
            int i = 0;
            out:
            for (JavaField field : fields) {
                String subTypeName = field.getType().getFullyQualifiedName();
                String fieldName = field.getName();
                if ("this$0".equals(fieldName) ||
                        "serialVersionUID".equals(fieldName) ||
                        DocClassUtil.isIgnoreFieldTypes(subTypeName)) {
                    continue;
                }
                Map<String, String> tagsMap = DocUtil.getFieldTagsValue(field);
                if (!isResp) {
                    if (tagsMap.containsKey(DocTags.IGNORE)) {
                        continue out;
                    }
                }
                List<JavaAnnotation> annotations = field.getAnnotations();
                for (JavaAnnotation annotation : annotations) {
                    String annotationName = annotation.getType().getSimpleName();
                    if (DocAnnotationConstants.SHORT_JSON_IGNORE.equals(annotationName) && isResp) {
                        continue out;
                    } else if (DocAnnotationConstants.SHORT_JSON_FIELD.equals(annotationName) && isResp) {
                        if (null != annotation.getProperty(DocAnnotationConstants.SERIALIZE_PROP)) {
                            if (Boolean.FALSE.toString().equals(annotation.getProperty(DocAnnotationConstants.SERIALIZE_PROP).toString())) {
                                continue out;
                            }
                        } else if (null != annotation.getProperty(DocAnnotationConstants.NAME_PROP)) {
                            fieldName = StringUtil.removeQuotes(annotation.getProperty(DocAnnotationConstants.NAME_PROP).toString());
                        }
                    } else if (DocAnnotationConstants.SHORT_JSON_PROPERTY.equals(annotationName) && isResp) {
                        if (null != annotation.getProperty(DocAnnotationConstants.VALUE_PROP)) {
                            fieldName = StringUtil.removeQuotes(annotation.getProperty(DocAnnotationConstants.VALUE_PROP).toString());
                        }
                    }
                }
                String typeSimpleName = field.getType().getSimpleName();

                String fieldGicName = field.getType().getGenericCanonicalName();
                data0.append("\"").append(fieldName).append("\":");
                if (DocClassUtil.isPrimitive(subTypeName)) {
                    String fieldValue = "";
                    if (tagsMap.containsKey(DocTags.MOCK) && StringUtil.isNotEmpty(tagsMap.get(DocTags.MOCK))) {
                        fieldValue = tagsMap.get(DocTags.MOCK);
                        if ("String".equals(typeSimpleName)) {
                            fieldValue = DocUtil.handleJsonStr(fieldValue);
                        }
                    } else {
                        fieldValue = DocUtil.getValByTypeAndFieldName(typeSimpleName, field.getName());
                    }
                    CustomRespField customResponseField = builder.getCustomRespFieldMap().get(fieldName);
                    if (null != customResponseField) {
                        Object val = customResponseField.getValue();
                        if (null != val) {
                            if ("String".equals(typeSimpleName)) {
                                data0.append(DocUtil.handleJsonStr(String.valueOf(val))).append(",");
                            } else {
                                data0.append(val).append(",");
                            }
                        } else {
                            data0.append(fieldValue).append(",");
                        }
                    } else {
                        data0.append(fieldValue).append(",");
                    }
                } else {
                    if (DocClassUtil.isCollection(subTypeName) || DocClassUtil.isArray(subTypeName)) {
                        fieldGicName = DocClassUtil.isArray(subTypeName) ? fieldGicName.substring(0, fieldGicName.indexOf("[")) : fieldGicName;
                        if (DocClassUtil.getSimpleGicName(fieldGicName).length == 0) {
                            data0.append("{\"object\":\"any object\"},");
                            continue out;
                        }
                        String gicName = DocClassUtil.getSimpleGicName(fieldGicName)[0];

                        if (DocGlobalConstants.JAVA_STRING_FULLY.equals(gicName)) {
                            data0.append("[").append("\"").append(buildJson(gicName, fieldGicName,  isResp, counter + 1, registryClasses,builder)).append("\"]").append(",");
                        } else if (DocGlobalConstants.JAVA_LIST_FULLY.equals(gicName)) {
                            data0.append("{\"object\":\"any object\"},");
                        } else if (gicName.length() == 1) {
                            if (globGicName.length == 0) {
                                data0.append("{\"object\":\"any object\"},");
                                continue out;
                            }
                            String gicName1 = (i < globGicName.length) ? globGicName[i] : globGicName[globGicName.length - 1];
                            if (DocGlobalConstants.JAVA_STRING_FULLY.equals(gicName1)) {
                                data0.append("[").append("\"").append(buildJson(gicName1, gicName1,  isResp, counter + 1, registryClasses,builder)).append("\"]").append(",");
                            } else {
                                if (!typeName.equals(gicName1)) {
                                    data0.append("[").append(buildJson(DocClassUtil.getSimpleName(gicName1), gicName1,  isResp, counter + 1, registryClasses,builder)).append("]").append(",");
                                } else {
                                    data0.append("[{\"$ref\":\"..\"}]").append(",");
                                }
                            }
                        } else {
                            if (!typeName.equals(gicName)) {
                                if (DocClassUtil.isMap(gicName)) {
                                    data0.append("[{\"mapKey\":{}}],");
                                    continue out;
                                }
                                data0.append("[").append(buildJson(gicName, fieldGicName, isResp, counter + 1, registryClasses,builder)).append("]").append(",");
                            } else {
                                data0.append("[{\"$ref\":\"..\"}]").append(",");
                            }
                        }
                    } else if (DocClassUtil.isMap(subTypeName)) {
                        if (DocClassUtil.isMap(fieldGicName)) {
                            data0.append("{").append("\"mapKey\":{}},");
                            continue out;
                        }
                        String gicName = fieldGicName.substring(fieldGicName.indexOf(",") + 1, fieldGicName.indexOf(">"));
                        if (gicName.length() == 1) {
                            String gicName1 = (i < globGicName.length) ? globGicName[i] : globGicName[globGicName.length - 1];
                            if (DocGlobalConstants.JAVA_STRING_FULLY.equals(gicName1)) {
                                data0.append("{").append("\"mapKey\":\"").append(buildJson(gicName1, gicName1,  isResp, counter + 1, registryClasses,builder)).append("\"},");
                            } else {
                                if (!typeName.equals(gicName1)) {
                                    data0.append("{").append("\"mapKey\":").append(buildJson(DocClassUtil.getSimpleName(gicName1), gicName1,  isResp, counter + 1, registryClasses,builder)).append("},");
                                } else {
                                    data0.append("{\"mapKey\":{}},");
                                }
                            }
                        } else {
                            data0.append("{").append("\"mapKey\":").append(buildJson(gicName, fieldGicName,  isResp, counter + 1, registryClasses,builder)).append("},");
                        }
                    } else if (subTypeName.length() == 1) {
                        if (!typeName.equals(genericCanonicalName)) {
                            String gicName = globGicName[i];
                            if (DocClassUtil.isPrimitive(gicName)) {
                                data0.append(DocUtil.jsonValueByType(gicName)).append(",");
                            } else {
                                String simple = DocClassUtil.getSimpleName(gicName);
                                data0.append(buildJson(simple, gicName,  isResp, counter + 1, registryClasses,builder)).append(",");
                            }
                        } else {
                            data0.append("{\"waring\":\"You may have used non-display generics.\"},");
                        }
                        i++;
                    } else if (DocGlobalConstants.JAVA_OBJECT_FULLY.equals(subTypeName)) {
                        if (isGenerics) {
                            data0.append("{\"object\":\"any object\"},");
                        } else if (i < globGicName.length) {
                            String gicName = globGicName[i];
                            if (!typeName.equals(genericCanonicalName)) {
                                if (DocClassUtil.isPrimitive(gicName)) {
                                    data0.append("\"").append(buildJson(gicName, genericCanonicalName, isResp, counter + 1, registryClasses,builder)).append("\",");
                                } else {
                                    String simpleName = DocClassUtil.getSimpleName(gicName);
                                    data0.append(buildJson(simpleName, gicName,  isResp, counter + 1, registryClasses,builder)).append(",");
                                }
                            } else {
                                data0.append("{\"waring\":\"You may have used non-display generics.\"},");
                            }
                        } else {
                            data0.append("{\"waring\":\"You may have used non-display generics.\"},");
                        }
                        if (!isGenerics) i++;
                    } else if (typeName.equals(subTypeName)) {
                        data0.append("{\"$ref\":\"...\"}").append(",");
                    } else {
                        JavaClass javaClass = builder.getJavaProjectBuilder().getClassByName(subTypeName);
                        if (!isResp && javaClass.isEnum()) {
                            Object value = JavaClassUtil.getEnumValue(javaClass, Boolean.FALSE);
                            data0.append(value).append(",");
                        } else {
                            data0.append(buildJson(subTypeName, fieldGicName,isResp, counter + 1, registryClasses,builder)).append(",");
                        }
                    }
                }
            }
        }
        if (data0.toString().contains(",")) {
            data0.deleteCharAt(data0.lastIndexOf(","));
        }
        data0.append("}");
        return data0.toString();
    }

    /**
     *
     * @param method Java method
     * @param apiMethodDoc apiMethodDoc
     * @param isPostMethod Response flag
     * @param docConfigBuilder docConfigBuilder
     * @return map : requestBody UrlParams
     */
    public static HashMap<String,String> buildReqJson(JavaMethod method, ApiMethodDoc apiMethodDoc, Boolean isPostMethod,ProjectDocConfigBuilder docConfigBuilder) {
        //post请求体
        String requestBody;
        //Url 参数
        String urlParams;
        boolean containsBrace = apiMethodDoc.getUrl().replace(DocGlobalConstants.DEFAULT_SERVER_URL,"").contains("{");
        HashMap<String,String> requestParams = new HashMap<>(4);
        List<JavaParameter> parameterList = method.getParameters();
        if (parameterList.size() < 1) {
            requestParams.put(JSON_GET_PARAMS,apiMethodDoc.getUrl());
            return requestParams;
        }
        //去掉mvcIgnoreParams
        parameterList.removeIf(javaParameter -> DocClassUtil.isMvcIgnoreParams(javaParameter.getFullyQualifiedName()));

        //处理@RequestParam 和 @PathVariable 放入请求的url
        Map<String, String> paramMap = ReqJsonUtil.buildGetParam(parameterList);
        urlParams = ReqJsonUtil.buildUrl(apiMethodDoc.getUrl(), containsBrace, paramMap);
        requestParams.put(JSON_GET_PARAMS,urlParams);

        //对post@RequestBody对象 和 表单处理
        if (isPostMethod) {
            //如果包含@RequestBody 就只处理注解标住的数据  则请求为json数据
            for (JavaParameter parameter : parameterList) {
                for (JavaAnnotation annotation : parameter.getAnnotations()) {
                    String annotationName = annotation.getType().getSimpleName();
                    if (DocGlobalConstants.SHORT_REQUEST_BODY.equals(annotationName) || DocGlobalConstants.REQUEST_BODY_FULLY.equals(annotationName)) {
                        JavaType javaType = parameter.getType();
                        String simpleTypeName = javaType.getValue();
                        String gicTypeName = javaType.getGenericCanonicalName();
                        String typeName = javaType.getFullyQualifiedName();
                        String paraName = parameter.getName();

                        apiMethodDoc.setContentType(DocGlobalConstants.APPLICATION_JSON);
                        if (DocClassUtil.isPrimitive(simpleTypeName)) {
                             requestBody =  "{\"" +
                                    paraName +
                                    "\":" +
                                    DocUtil.jsonValueByType(simpleTypeName) +
                                    "}";
                            requestParams.put(JSON_REQUEST_BODY,requestBody);
                            return requestParams;
                        } else {
                            requestBody  = buildJson(typeName, gicTypeName, false, 0,new HashMap<>(20),docConfigBuilder);
                            requestParams.put(JSON_REQUEST_BODY,requestBody);
                            return requestParams;
                        }
                    }
                }
            }
            //否则为 formData 数据
            apiMethodDoc.setContentType(DocGlobalConstants.POSTMAN_MODE_FORMDATA);
            List<FormData> formDataList = new ArrayList<>();
            //构建formData数据
            param:
            for (JavaParameter parameter : parameterList) {
                String paraName = parameter.getName();
                JavaType javaType = parameter.getType();
                String typeName = javaType.getFullyQualifiedName();

                FormData formData = new FormData();
                //排除header头部
                for (JavaAnnotation annotation : parameter.getAnnotations()) {
                    if (annotation.getType().getSimpleName().equals(DocAnnotationConstants.SHORT_REQUSRT_HEADER)) {
                        continue param;
                    }
                }
                formData.setKey(paraName);
                formData.setValue(buildFormDataParam(parameter,docConfigBuilder));
                //postman 暂不支持多文件上传 多文件上传只显示一个文件
                if (DocClassUtil.isFormDataFile(typeName)) {
                    formData.setType("file");
                    formData.setValue("can not support multiple MultipartFile");
                } else {
                    formData.setType("text");
                }
                formDataList.add(formData);
            }
            //formData数据逐个解析
            requestBody = JsonFormatUtil.formatJson(new Gson().toJson(formDataList));
            requestParams.put(JSON_REQUEST_BODY,requestBody);
            return requestParams;
        }
        return requestParams;
    }

    /**
     * 构建FormData数据
     *
     * @param javaParameter 参数列表
     * @return formData 数据
     */
    private static String buildFormDataParam(JavaParameter javaParameter, ProjectDocConfigBuilder builder) {
        JavaType javaType = javaParameter.getType();
        String simpleTypeName = javaType.getValue();
        String typeName = javaType.getFullyQualifiedName();
        String paraName = javaParameter.getName();
        String gicTypeName = javaType.getGenericCanonicalName();

        //是基本的数据类型
        if (DocClassUtil.isPrimitive(typeName)) {
            return DocUtil.getValByTypeAndFieldName(simpleTypeName, paraName, true);
        }
        //是基本数据类型的数组
        if (DocClassUtil.isPrimitiveArray(typeName)) {
            return DocUtil.getValByTypeAndFieldName(simpleTypeName, paraName, true);
        }
        //是文件数据
        if (DocClassUtil.isFormDataFile(typeName)) {
            return "File";
        }
        //是一个复杂对象
        else {
            String data = buildJson(typeName, gicTypeName , false, 0, new HashMap<>(20),builder);
            data = data.replaceAll("\"", "");
            data = data.replaceAll("\n", "");
            return data;

        }
    }




}
