/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wicket.injection;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.Page;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.util.collections.ClassMetaCache;

/**
 * Injector scans fields of an object instance and checks if the specified
 * {@link IFieldValueFactory} can provide a value for a field; if it can, the field is set to that
 * value. Injector will ignore all non-null fields.
 * 
 * @author Igor Vaynberg (ivaynberg)
 * 
 */
public class Injector
{
	private static final Field[] EMPTY_FIELDS = new Field[0];

	private static Injector instance = new Injector();

	private final ClassMetaCache<Field[]> cache = new ClassMetaCache<Field[]>();

	/**
	 * @return static instance of ProxyInjector
	 */
	public static Injector getInstance()
	{
		return instance;
	}

	/**
	 * When the initializer traverses the hierarchy of the specified object it will stop if it
	 * encounters a boundary class.
	 * 
	 * By default, more common wicket classes are defined as boundaries so that the initializer does
	 * not waste time traversing them.
	 * 
	 * @param clazz
	 *            class to be tested for being a boundary class
	 * @return true if the class is a boundary class, false otherwise
	 */
	protected boolean isBoundaryClass(Class<?> clazz)
	{
		if (clazz.equals(WebPage.class) || clazz.equals(Page.class) || clazz.equals(Panel.class) ||
			clazz.equals(MarkupContainer.class) || clazz.equals(Component.class))
		{
			return true;
		}
		return false;
	}

	/**
	 * traverse fields in the class hierarchy of the object and set their value with a locator
	 * provided by the locator factory.
	 * 
	 * @param object
	 * @param factory
	 * @return Object that was injected - used for chaining
	 */
	public Object inject(Object object, IFieldValueFactory factory)
	{
		final Class<?> clazz = object.getClass();

		Field[] fields = getFields(clazz, factory);

		for (int i = 0; i < fields.length; i++)
		{
			final Field field = fields[i];

			if (!field.isAccessible())
			{
				field.setAccessible(true);
			}
			try
			{

				if (field.get(object) == null)
				{

					Object value = factory.getFieldValue(field, object);

					if (value != null)
					{
						field.set(object, value);
					}
				}
			}
			catch (IllegalArgumentException e)
			{
				throw new RuntimeException("error while injecting object [" + object.toString() +
					"] of type [" + object.getClass().getName() + "]", e);
			}
			catch (IllegalAccessException e)
			{
				throw new RuntimeException("error while injecting object [" + object.toString() +
					"] of type [" + object.getClass().getName() + "]", e);
			}
		}

		return object;
	}

	/**
	 * caches results of {@link #getFields(Class, IFieldValueFactory)}
	 * 
	 * @param clazz
	 * @param factory
	 * @return cached results as returned by {@link #getFields(Class, IFieldValueFactory)}
	 */
	private Field[] getFields(Class<?> clazz, IFieldValueFactory factory)
	{
		Field[] fields = cache.get(clazz);

		if (fields == null)
		{
			fields = findFields(clazz, factory);

			// write to cache
			cache.put(clazz, fields);
		}

		return fields;
	}

	/**
	 * Returns an array of fields that can be injected using the given field value factory
	 * 
	 * @param clazz
	 * @param factory
	 * @return an array of fields that can be injected using the given field value factory
	 */
	private Field[] findFields(Class<?> clazz, IFieldValueFactory factory)
	{
		List<Field> matched = new ArrayList<Field>();

		while (clazz != null && !isBoundaryClass(clazz))
		{
			Field[] fields = clazz.getDeclaredFields();
			for (int i = 0; i < fields.length; i++)
			{
				final Field field = fields[i];

				if (factory.supportsField(field))
				{
					matched.add(field);
				}
			}
			clazz = clazz.getSuperclass();
		}

		return matched.size() == 0 ? EMPTY_FIELDS : matched.toArray(new Field[matched.size()]);
	}
}
