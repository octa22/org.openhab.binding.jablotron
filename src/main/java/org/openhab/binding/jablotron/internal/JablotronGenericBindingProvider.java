/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.jablotron.internal;

import org.openhab.binding.jablotron.JablotronBindingProvider;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.DateTimeItem;
import org.openhab.core.library.items.DimmerItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;


/**
 * This class is responsible for parsing the binding configuration.
 * 
 * @author Ondrej Pecta
 * @since 1.9.0
 */
public class JablotronGenericBindingProvider extends AbstractGenericBindingProvider implements JablotronBindingProvider {

	/**
	 * {@inheritDoc}
	 */
	public String getBindingType() {
		return "jablotron";
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public void validateItemType(Item item, String bindingConfig) throws BindingConfigParseException {
		if (!(item instanceof SwitchItem || item instanceof DateTimeItem)) {
			throw new BindingConfigParseException("item '" + item.getName()
					+ "' is of type '" + item.getClass().getSimpleName()
					+ "', only Switch- and DateTimeItems are allowed - please check your *.items configuration");
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processBindingConfiguration(String context, Item item, String bindingConfig) throws BindingConfigParseException {
		super.processBindingConfiguration(context, item, bindingConfig);
		JablotronBindingConfig config = new JablotronBindingConfig(bindingConfig);
		
		//parse bindingconfig here ...
		
		addBindingConfig(item, config);		
	}

	@Override
	public String getSection(String itemName) {
		final JablotronBindingConfig config = (JablotronBindingConfig) this.bindingConfigs.get(itemName);
		return config.getSection();
	}


	/**
	 * This is a helper class holding binding specific configuration details
	 * 
	 * @author Ondrej Pecta
	 * @since 1.9.0
	 */
	class JablotronBindingConfig implements BindingConfig {
		// put member fields here which holds the parsed values
		private String section;

		public String getSection() {
			return section;
		}

		JablotronBindingConfig(String section) {
			this.section = section;
		}
	}
	
	
}
