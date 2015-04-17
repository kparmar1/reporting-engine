/*
 * Copyright 2011-2015 B2i Healthcare Pte Ltd, http://b2i.sg
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ihtsdo.snowowl.api.rest.common;

import com.b2international.snowowl.api.domain.IComponentRef;
import com.b2international.snowowl.api.impl.domain.ComponentRef;

/**
 * Abstract SNOMED CT REST service base class.
 * 
 * @since 1.0
 */
public class AbstractSnomedRestService extends AbstractRestService {

	private final ComponentRefHelper componentRefHelper;

	public AbstractSnomedRestService() {
		componentRefHelper = new ComponentRefHelper();
	}

	protected IComponentRef createComponentRef(final String version, final String taskId, final String componentId) {
		IComponentRef componentRef = componentRefHelper.createComponentRef(version, taskId, componentId);
		((ComponentRef)componentRef).checkStorageExists();
		return componentRef;
	}
}