/**
 * Copyright 2009-2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.AmbiguousMethodInvoker;
import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 * 通过将Class封装成一个Reflector对象，在对象中缓存Class的元数据信息，提高反射的执行效率
 */
public class Reflector {

    // 要被反射解析的类
    private final Class<?> type;
    // 能够读的属性列表，即有get方法的属性列表
    private final String[] readablePropertyNames;
    // 能够写的属性列表，即有set方法的属性列表
    private final String[] writablePropertyNames;
    //  setter 映射表，键是属性名，值是对应的setter方法
    private final Map<String, Invoker> setMethods = new HashMap<>();
    //  getter 映射表，键是属性名，值是对应的getter方法
    private final Map<String, Invoker> getMethods = new HashMap<>();
    // set方法输入类型。键为属性名，值为对应的该属性的set方法的类型（实际为set方法的第一个参数的类型）
    private final Map<String, Class<?>> setTypes = new HashMap<>();
    // get方法返回类型映射表。键为属性名，值为对应的该属性的set方法的类型（实际为set方法的返回值类型）
    private final Map<String, Class<?>> getTypes = new HashMap<>();
    // 默认构造方法
    private Constructor<?> defaultConstructor;

    // 大小写无关的属性映射表。键为属性名全大写值，值为属性名
    private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

    //    构造方法，通过传入一个类型来实现
    public Reflector(Class<?> clazz) {
//        记录当前对象管理的类型
        type = clazz;
//        设置默认构造方法
        addDefaultConstructor(clazz);
//        设置getter方法
        addGetMethods(clazz);
//        设置setter方法
        addSetMethods(clazz);
//        处理没有getter/setter的属性
        addFields(clazz);
//        保存有getter方法的属性的名称
        readablePropertyNames = getMethods.keySet().toArray(new String[0]);
//        保存有setter方法的属性的名称
        writablePropertyNames = setMethods.keySet().toArray(new String[0]);
//        将这些可读/可写的属性保存到map里
        for (String propName : readablePropertyNames) {
            caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
        }
        for (String propName : writablePropertyNames) {
            caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
        }
    }

    /**
     * 获取默认构造函数的方法
     *
     * @param clazz 当前reflector包装的类型
     */
    private void addDefaultConstructor(Class<?> clazz) {
        // 获取该类的所有构造器
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        //遍历所有构造器，如果存在无参构造器就放入
        Arrays.stream(constructors).filter(constructor -> constructor.getParameterTypes().length == 0)
                .findAny().ifPresent(constructor -> this.defaultConstructor = constructor);
    }

    /**
     * 获取该类的getter
     *
     * @param clazz reflector包装的类型
     */
    private void addGetMethods(Class<?> clazz) {
        // 用于存放获取到的对应类 所有 getter的map，可能存在一个属性对应多个getter的情况
        Map<String, List<Method>> conflictingGetters = new HashMap<>();
        // 找出该类中所有的方法
        Method[] methods = getClassMethods(clazz);
        // 过滤getter，条件是1：无参 2：是get/is开头，放入conflictingGetters，键是方法名去掉get/is后将首字母小写的字符串
        Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
                .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));
        resolveGetterConflicts(conflictingGetters);
    }

    /**
     * 处理map中一个属性对应多个getter的情况，这种情况大多是由于重写导致的，子类重写父类的getter方法，且方法签名不同
     *
     * @param conflictingGetters 存储getter方法的map，键是属性名，值是对应的getter方法的Method类的集合
     */
    private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
        for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
            // 处理每一个属性名对应的getter集合
            Method winner = null;
            String propName = entry.getKey();
            boolean isAmbiguous = false;
            for (Method candidate : entry.getValue()) {
                // 这个属性对应的第一个getter方法作为暂时的胜出者
                if (winner == null) {
                    winner = candidate;
                    continue;
                }
                Class<?> winnerType = winner.getReturnType();
                Class<?> candidateType = candidate.getReturnType();
                if (candidateType.equals(winnerType)) {
                    // 如果两个getter的返回类型相同且不是boolean，则出现了歧义
                    if (!boolean.class.equals(candidateType)) {
                        isAmbiguous = true;
                        break;
                        // 如果是返回类型都是boolean类型且后面的getter是is开头，就让后续getter的当选
                    } else if (candidate.getName().startsWith("is")) {
                        winner = candidate;
                    }
                } else if (candidateType.isAssignableFrom(winnerType)) {
                    // OK getter type is descendant
                    // 如果另外的getter的返回类型是前面的getter的子类，让返回类型是子类的当选
                } else if (winnerType.isAssignableFrom(candidateType)) {
                    winner = candidate;
                    // 即不相等，也不是前面的子类，出现歧义
                } else {
                    isAmbiguous = true;
                    break;
                }
            }
            addGetMethod(propName, winner, isAmbiguous);
        }
    }

    /**
     * 将getter和其对应的属性放入getter方法的映射表，键是属性名，值是MethodInvoker，其中组合了这个getter对应的Method对象
     *
     * @param name        属性名
     * @param method      getter方法
     * @param isAmbiguous 是否有歧义
     */
    private void addGetMethod(String name, Method method, boolean isAmbiguous) {
        // 上一步出现歧义和未出现，在这一步使用不同的类包装
        MethodInvoker invoker = isAmbiguous
                ? new AmbiguousMethodInvoker(method, MessageFormat.format(
                "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
                name, method.getDeclaringClass().getName()))
                : new MethodInvoker(method);
        getMethods.put(name, invoker);
        // 处理这个getter的返回类型，获取其对应的实际类型，并放入getter方法返回类型映射表getTypes
        Type returnType = TypeParameterResolver.resolveReturnType(method, type);
        getTypes.put(name, typeToClass(returnType));
    }

    /**
     * 获取该类所有的setter存入setMethods映射表
     *
     * @param clazz reflector包裹的具体类型
     */
    private void addSetMethods(Class<?> clazz) {
        Map<String, List<Method>> conflictingSetters = new HashMap<>();
        Method[] methods = getClassMethods(clazz);
        // 过滤出只有一个参数，以set开头且不名为set（条件是m.getName().length()>3）的方法
        // setter方法映射表的键是属性名，值是setter的Method对象
        Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
                .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
        // 处理一个属性有多个setter的情况
        resolveSetterConflicts(conflictingSetters);
    }

    private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
        if (isValidPropertyName(name)) {
            List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
            list.add(method);
        }
    }

    /**
     * 处理一个属性有多个setter的情况
     *
     * @param conflictingSetters 包括可能存在的多个setter情况的setter映射表
     */
    private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
        for (Entry<String, List<Method>> entry : conflictingSetters.entrySet()) {
            String propName = entry.getKey();
            List<Method> setters = entry.getValue();
            Class<?> getterType = getTypes.get(propName);
            boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
            boolean isSetterAmbiguous = false;
            Method match = null;
            for (Method setter : setters) {
                // 如果参数类型与对应getter的返回类型相同，就选择它（前提是处理getter时没有歧义）
                if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
                    // should be the best match
                    match = setter;
                    break;
                }
                if (!isSetterAmbiguous) {
                    // 有多个setter选出最合适的，判断标准是选择接收的参数类型最严格的那个，如List 和 ArrayList -> 选择参数类型是ArrayList的那个
                    match = pickBetterSetter(match, setter, propName);
                    // 如果前面没有选出最终的setter，说明存在歧义，isSetterAmbiguous置为true
                    isSetterAmbiguous = match == null;
                }
            }
            // 如果选到了最合适的getter就把这个属性和其对应的setter的Method对象存入setMethods
            if (match != null) {
                // 这个方法里还同时填充了setTypes映射表
                addSetMethod(propName, match);
            }
        }
    }

    /**
     * 从两个setter中选择参数类型更严格的那个
     *
     * @param property 对应的属性名
     * @return 参数类型更严格的那个
     */
    private Method pickBetterSetter(Method setter1, Method setter2, String property) {
        if (setter1 == null) {
            return setter2;
        }
        Class<?> paramType1 = setter1.getParameterTypes()[0];
        Class<?> paramType2 = setter2.getParameterTypes()[0];
//        判断标准是选择接收的参数类型最严格的那个，如List 和 ArrayList -> 选择参数类型是ArrayList的那个
        if (paramType1.isAssignableFrom(paramType2)) {
            return setter2;
        } else if (paramType2.isAssignableFrom(paramType1)) {
            return setter1;
        }
        MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
                MessageFormat.format(
                        "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.",
                        property, setter2.getDeclaringClass().getName(), paramType1.getName(), paramType2.getName()));
        setMethods.put(property, invoker);
        Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
        setTypes.put(property, typeToClass(paramTypes[0]));
        return null;
    }

    private void addSetMethod(String name, Method method) {
        MethodInvoker invoker = new MethodInvoker(method);
        setMethods.put(name, invoker);
        Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
        setTypes.put(name, typeToClass(paramTypes[0]));
    }

    private Class<?> typeToClass(Type src) {
        Class<?> result = null;
        if (src instanceof Class) {
            result = (Class<?>) src;
        } else if (src instanceof ParameterizedType) {
            result = (Class<?>) ((ParameterizedType) src).getRawType();
        } else if (src instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) src).getGenericComponentType();
            if (componentType instanceof Class) {
                result = Array.newInstance((Class<?>) componentType, 0).getClass();
            } else {
                Class<?> componentClass = typeToClass(componentType);
                result = Array.newInstance(componentClass, 0).getClass();
            }
        }
        if (result == null) {
            result = Object.class;
        }
        return result;
    }

    /**
     * 记录该类的所有属性
     *
     * @param clazz reflector对象包裹的类型
     */
    private void addFields(Class<?> clazz) {
        // 通过反射获取该类型所有的属性并遍历，包括静态和非静态的属性
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            // 如果这个属性不存在setMethod映射表中，即没有确定对应的setter方法
            if (!setMethods.containsKey(field.getName())) {
                // issue #379 - removed the check for final because JDK 1.5 allows
                // modification of final fields through reflection (JSR-133). (JGB)
                // pr #16 - final static can only be set by the classloader
                // 获取该属性的修饰符，这里返回值是整型，是多个修饰符加起来的值，如private static 是10，public static是9
                int modifiers = field.getModifiers();
                // 如果没被final修饰，且被static修饰，给这个属性加入setMethods映射表和setTypes映射表，给他生成一个代理，作为他的setter方法
                if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
                    addSetField(field);
                }
            }
            // 如果这个属性不存在setMethods映射表，给他生成一个代理，作为他的getter方法，并存入setMethods和setTypes
            if (!getMethods.containsKey(field.getName())) {
                addGetField(field);
            }
        }
        // 如果这个类型有父类，将他父类的属性也按照该方法的逻辑添加到getMethods、setMethods、getTypes、setType映射表。
        // 因为getdeclaredFields()不会获取从父类继承来的属性
        if (clazz.getSuperclass() != null) {
            addFields(clazz.getSuperclass());
        }
    }

    private void addSetField(Field field) {
        if (isValidPropertyName(field.getName())) {
            setMethods.put(field.getName(), new SetFieldInvoker(field));
            Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
            setTypes.put(field.getName(), typeToClass(fieldType));
        }
    }

    private void addGetField(Field field) {
        if (isValidPropertyName(field.getName())) {
            getMethods.put(field.getName(), new GetFieldInvoker(field));
            Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
            getTypes.put(field.getName(), typeToClass(fieldType));
        }
    }

    private boolean isValidPropertyName(String name) {
        return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
    }

    /**
     * This method returns an array containing all methods
     * declared in this class and any superclass.
     * We use this method, instead of the simpler <code>Class.getMethods()</code>,
     * because we want to look for private methods as well.
     *
     * @param clazz The class
     * @return An array containing all methods in this class
     */
    private Method[] getClassMethods(Class<?> clazz) {
        Map<String, Method> uniqueMethods = new HashMap<>();
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

            // we also need to look for interface methods -
            // because the class may be abstract
            // 处理抽象类的情况
            Class<?>[] interfaces = currentClass.getInterfaces();
            for (Class<?> anInterface : interfaces) {
                addUniqueMethods(uniqueMethods, anInterface.getMethods());
            }

            currentClass = currentClass.getSuperclass();
        }

        Collection<Method> methods = uniqueMethods.values();

        return methods.toArray(new Method[0]);
    }

    private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
        for (Method currentMethod : methods) {
            // 判断方法不是桥接方法，桥接方法和Java的泛型的类型擦除有关
            if (!currentMethod.isBridge()) {
                // 获取方法签名，格式是：String#getName:java.lang.String,java.lang.Integer
                String signature = getSignature(currentMethod);
                // check to see if the method is already known
                // if it is known, then an extended class must have
                // overridden a method
                // 当实现类重写了接口的方法后，因为需要填充抽象类可能没实现的方法，所以可能同一个签名出现多次，一次是实现类的方法，一次是接口的方法
                // 因为先调用实现类的getDeclaredMethods()，所以如果map中有了，说明重写了，不要在添加接口获取的这个签名对应的方法，防止覆盖。
                if (!uniqueMethods.containsKey(signature)) {
                    uniqueMethods.put(signature, currentMethod);
                }
            }
        }
    }

    /**
     * 获取方法的签名， 格式为：String#getName:java.lang.String,java.lang.Integer
     *
     * @param method 具体的方法
     * @return 上述格式的方法签名
     */
    private String getSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        Class<?> returnType = method.getReturnType();
        if (returnType != null) {
            sb.append(returnType.getName()).append('#');
        }
        sb.append(method.getName());
        Class<?>[] parameters = method.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
        }
        return sb.toString();
    }

    /**
     * Checks whether can control member accessible.
     * 判断能否修改成员的访问权限
     * @return If can control member accessible, it return {@literal true}
     * @since 3.5.0
     */
    public static boolean canControlMemberAccessible() {
        try {
            SecurityManager securityManager = System.getSecurityManager();
            if (null != securityManager) {
                securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
            }
        } catch (SecurityException e) {
            return false;
        }
        return true;
    }

    /**
     * Gets the name of the class the instance provides information for.
     *
     * @return The class name
     */
    public Class<?> getType() {
        return type;
    }

    public Constructor<?> getDefaultConstructor() {
        if (defaultConstructor != null) {
            return defaultConstructor;
        } else {
            throw new ReflectionException("There is no default constructor for " + type);
        }
    }

    public boolean hasDefaultConstructor() {
        return defaultConstructor != null;
    }

    public Invoker getSetInvoker(String propertyName) {
        Invoker method = setMethods.get(propertyName);
        if (method == null) {
            throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
        }
        return method;
    }

    public Invoker getGetInvoker(String propertyName) {
        Invoker method = getMethods.get(propertyName);
        if (method == null) {
            throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
        }
        return method;
    }

    /**
     * Gets the type for a property setter.
     *
     * @param propertyName - the name of the property
     * @return The Class of the property setter
     */
    public Class<?> getSetterType(String propertyName) {
        Class<?> clazz = setTypes.get(propertyName);
        if (clazz == null) {
            throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
        }
        return clazz;
    }

    /**
     * Gets the type for a property getter.
     *
     * @param propertyName - the name of the property
     * @return The Class of the property getter
     */
    public Class<?> getGetterType(String propertyName) {
        Class<?> clazz = getTypes.get(propertyName);
        if (clazz == null) {
            throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
        }
        return clazz;
    }

    /**
     * Gets an array of the readable properties for an object.
     *
     * @return The array
     */
    public String[] getGetablePropertyNames() {
        return readablePropertyNames;
    }

    /**
     * Gets an array of the writable properties for an object.
     *
     * @return The array
     */
    public String[] getSetablePropertyNames() {
        return writablePropertyNames;
    }

    /**
     * Check to see if a class has a writable property by name.
     *
     * @param propertyName - the name of the property to check
     * @return True if the object has a writable property by the name
     */
    public boolean hasSetter(String propertyName) {
        return setMethods.containsKey(propertyName);
    }

    /**
     * Check to see if a class has a readable property by name.
     *
     * @param propertyName - the name of the property to check
     * @return True if the object has a readable property by the name
     */
    public boolean hasGetter(String propertyName) {
        return getMethods.containsKey(propertyName);
    }

    public String findPropertyName(String name) {
        return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
    }
}
