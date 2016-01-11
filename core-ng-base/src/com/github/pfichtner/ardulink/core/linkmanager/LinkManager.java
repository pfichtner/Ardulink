package com.github.pfichtner.ardulink.core.linkmanager;

import static com.github.pfichtner.beans.finder.impl.FindByAnnotation.propertyAnnotated;
import static java.lang.String.format;
import static org.zu.ardulink.util.Preconditions.checkArgument;
import static org.zu.ardulink.util.Preconditions.checkNotNull;
import static org.zu.ardulink.util.Preconditions.checkState;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;
import java.util.ServiceLoader;

import org.zu.ardulink.util.Lists;
import org.zu.ardulink.util.Primitive;

import com.github.pfichtner.ardulink.core.Link;
import com.github.pfichtner.ardulink.core.linkmanager.LinkConfig.ChoiceFor;
import com.github.pfichtner.ardulink.core.linkmanager.LinkConfig.I18n;
import com.github.pfichtner.ardulink.core.linkmanager.LinkConfig.Named;
import com.github.pfichtner.beans.Attribute;
import com.github.pfichtner.beans.BeanProperties;

public abstract class LinkManager {

	public interface ConfigAttribute {

		/**
		 * Returns the type of this attribute.
		 * 
		 * @return type
		 */
		Class<?> getType();

		/**
		 * Returns the current value of this attribute.
		 * 
		 * @return current value
		 */
		Object getValue();

		/**
		 * Sets the new value of this attribute. If this attribute
		 * hasChoiceValues only one of those values can be set!
		 */
		void setValue(Object value);

		/**
		 * Returns <code>true</code> if this attribute has predefined choice
		 * values.
		 * 
		 * @return <code>true</code> if this attribute has predefined choice
		 *         values
		 * @see #getChoiceValues()
		 */
		boolean hasChoiceValues();

		/**
		 * Returns the choice values (if any) of this attribute.
		 * 
		 * @return the available choice values
		 * @see #hasChoiceValues()
		 */
		Object[] getChoiceValues();

		/**
		 * Returns the localized name of this attribute.
		 * 
		 * @return localized name of this attribute
		 */
		String getLocalizedName();

	}

	public static class ConfigAttributeAdapter<T extends LinkConfig> implements
			ConfigAttribute {

		private final Attribute attribute;
		private final Attribute getChoicesFor;
		private final ResourceBundle nls;
		private List<Object> cachedChoiceValues;

		public ConfigAttributeAdapter(T linkConfig,
				BeanProperties beanProperties, String key) {
			this.attribute = beanProperties.getAttribute(key);
			checkArgument(attribute != null,
					"Could not determine attribute %s", key);
			this.getChoicesFor = BeanProperties.builder(linkConfig)
					.using(propertyAnnotated(ChoiceFor.class)).build()
					.getAttribute(attribute.getName());
			I18n nls = linkConfig.getClass().getAnnotation(I18n.class);
			this.nls = nls == null ? null : ResourceBundle.getBundle(nls
					.value());
		}

		@Override
		public Class<?> getType() {
			return attribute.getType();
		}

		@Override
		public Object getValue() {
			try {
				return this.attribute.readValue();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void setValue(Object value) {
			if (hasChoiceValues() && this.cachedChoiceValues == null) {
				try {
					this.cachedChoiceValues = Arrays.asList(getChoiceValues());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			checkArgument(this.cachedChoiceValues == null
					|| this.cachedChoiceValues.contains(value),
					"%s is not a valid value for %s, valid values are %s",
					value, this.attribute.getName(), this.cachedChoiceValues);
			try {
				this.attribute.writeValue(value);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public boolean hasChoiceValues() {
			return this.getChoicesFor != null;
		}

		@Override
		public Object[] getChoiceValues() {
			checkState(hasChoiceValues(),
					"attribute does not have choiceValues");
			try {
				Object[] value = loadChoiceValues();
				this.cachedChoiceValues = Arrays.asList(value);
				return value;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		private Object[] loadChoiceValues() throws Exception {
			Object value = checkNotNull(this.getChoicesFor.readValue(),
					"returntype was null (should be an empty Object[] or empty Collection)");
			if (value instanceof Collection<?>) {
				value = ((Collection<?>) value).toArray(new Object[0]);
			}
			checkState(value instanceof Object[],
					"returntype is not an Object[] but %s",
					value == null ? null : value.getClass());
			return (Object[]) value;
		}

		@Override
		public String getLocalizedName() {
			return nls == null || !nls.containsKey(this.attribute.getName()) ? null
					: nls.getString(this.attribute.getName());
		}

	}

	public interface Configurer {

		Collection<String> getAttributes();

		ConfigAttribute getAttribute(String key);

		Link newLink() throws Exception;

	}

	public static class DefaultConfigurer<T extends LinkConfig> implements
			Configurer {

		private final LinkFactory<T> linkFactory;
		private final T linkConfig;
		private BeanProperties beanProperties;

		public DefaultConfigurer(LinkFactory<T> connectionFactory) {
			this.linkFactory = connectionFactory;
			this.linkConfig = connectionFactory.newLinkConfig();
			this.beanProperties = BeanProperties.builder(linkConfig)
					.using(propertyAnnotated(Named.class)).build();
		}

		@Override
		public Collection<String> getAttributes() {
			try {
				return beanProperties.attributeNames();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		public ConfigAttribute getAttribute(String key) {
			return new ConfigAttributeAdapter<T>(linkConfig, beanProperties,
					key);
		}

		@Override
		public Link newLink() throws Exception {
			return this.linkFactory.newLink(this.linkConfig);
		}

	}

	private static final String SCHEMA = "ardulink";

	public static LinkManager getInstance() {
		return new LinkManager() {

			@Override
			public List<URI> listURIs() {
				List<LinkFactory> factories = getConnectionFactories();
				List<URI> result = new ArrayList<URI>(factories.size());
				for (LinkFactory<?> factory : factories) {
					String name = factory.getName();
					try {
						result.add(new URI(format("%s://%s", SCHEMA, name)));
					} catch (URISyntaxException e) {
						throw new RuntimeException(e);
					}
				}
				return result;
			}

			private LinkFactory<?> getConnectionFactory(String name) {
				for (LinkFactory<?> connectionFactory : getConnectionFactories()) {
					if (connectionFactory.getName().equals(name)) {
						return connectionFactory;
					}
				}
				return null;
			}

			private List<LinkFactory> getConnectionFactories() {
				return Lists.newArrayList(ServiceLoader.load(LinkFactory.class)
						.iterator());
			}

			@Override
			public Configurer getConfigurer(URI uri) {
				String name = getHostFromCheckedSchema(uri);
				LinkFactory connectionFactory = getConnectionFactory(name);
				checkArgument(
						connectionFactory != null,
						"No factory registered for \"%s\", available names are %s",
						name, listURIs());
				DefaultConfigurer defaultConfigurer = new DefaultConfigurer(
						connectionFactory);
				return configure(defaultConfigurer,
						uri.getQuery() == null ? new String[0] : uri.getQuery()
								.split("\\&"));
			}

			private Configurer configure(Configurer configurer, String[] params) {
				for (String param : params) {
					String[] split = param.split("\\=");
					if (split.length == 2) {
						ConfigAttribute attribute = configurer
								.getAttribute(split[0]);
						attribute.setValue(convert(split[1],
								attribute.getType()));
					}
				}
				return configurer;
			}

			private Object convert(String value, Class<?> targetType) {
				return targetType.isInstance(value) ? value : Primitive
						.parseAs(targetType, value);
			}

		};
	}

	public static String getHostFromCheckedSchema(URI uri) {
		return checkSchema(uri).getHost();
	}

	private static URI checkSchema(URI uri) {
		checkArgument(SCHEMA.equalsIgnoreCase(uri.getScheme()),
				"schema not %s (was %s)", SCHEMA, uri.getScheme());
		return uri;
	}

	/**
	 * Returns a newly created {@link Configurer} for the passed {@link URI}.
	 * Configurers should <b>not</b> be shared amongst threads since there is no
	 * guarantee that they are threadsafe. Beside that their values are
	 * retrieved to calculate cache keys for sharing Link instances which should
	 * not be done in parallel, too.
	 * 
	 * @param uri
	 *            the URI to create the new Configurer for
	 * @return newly created Configurer for the passed URI
	 */
	public abstract Configurer getConfigurer(URI uri);

	/**
	 * List all available (registered) URIs. Can be empty if no factory is
	 * registered but never is <code>null</code>.
	 * 
	 * @return all available URIs.
	 */
	public abstract List<URI> listURIs();

}