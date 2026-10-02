/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Wick Dynex
 */
@Controller
@RequestMapping("/owners/{ownerId}")
class PetController {

	private static final String VIEWS_PETS_CREATE_OR_UPDATE_FORM = "pets/createOrUpdatePetForm";

	private final OwnerRepository owners;

	private final PetTypeRepository types;

	private final TransactionTemplate transactionTemplate;

	public PetController(OwnerRepository owners, PetTypeRepository types,
			PlatformTransactionManager transactionManager) {
		this.owners = owners;
		this.types = types;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@ModelAttribute("types")
	public Collection<PetType> populatePetTypes() {
		return this.types.findPetTypes();
	}

	@ModelAttribute("owner")
	public Owner findOwner(@PathVariable("ownerId") int ownerId) {
		Optional<Owner> optionalOwner = this.owners.findById(ownerId);
		Owner owner = optionalOwner.orElseThrow(() -> new IllegalArgumentException(
				"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));
		return owner;
	}

	@ModelAttribute("pet")
	public Pet findPet(@PathVariable("ownerId") int ownerId,
			@PathVariable(name = "petId", required = false) Integer petId) {

		if (petId == null) {
			return new Pet();
		}

		Optional<Owner> optionalOwner = this.owners.findById(ownerId);
		Owner owner = optionalOwner.orElseThrow(() -> new IllegalArgumentException(
				"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));
		return owner.getPet(petId);
	}

	@InitBinder("owner")
	public void initOwnerBinder(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id", "*.id");
	}

	@InitBinder("pet")
	public void initPetBinder(WebDataBinder dataBinder) {
		dataBinder.setValidator(new PetValidator());
		dataBinder.setDisallowedFields("id", "*.id");
	}

	@GetMapping("/pets/new")
	public String initCreationForm(Owner owner, ModelMap model) {
		Pet pet = new Pet();
		owner.addPet(pet);
		return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/pets/new")
	public String processCreationForm(@PathVariable("ownerId") int ownerId, Owner owner, @Valid Pet pet,
			BindingResult result, RedirectAttributes redirectAttributes) {

		if (StringUtils.hasText(pet.getName()) && pet.isNew() && owner.getPet(pet.getName(), true) != null) {
			result.rejectValue("name", "duplicate", "already exists");
		}

		LocalDate currentDate = LocalDate.now();
		if (pet.getBirthDate() != null && pet.getBirthDate().isAfter(currentDate)) {
			result.rejectValue("birthDate", "typeMismatch.birthDate");
		}

		if (result.hasErrors()) {
			return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
		}

		boolean added;
		try {
			added = addPetIfNameAvailable(ownerId, pet);
		}
		catch (DataIntegrityViolationException ex) {
			if (!isDuplicatePetNameViolation(ex)) {
				throw ex;
			}
			added = false;
		}
		if (!added) {
			result.rejectValue("name", "duplicate", "already exists");
			return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
		}
		redirectAttributes.addFlashAttribute("message", "New Pet has been Added");
		return "redirect:/owners/{ownerId}";
	}

	@GetMapping("/pets/{petId}/edit")
	public String initUpdateForm() {
		return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/pets/{petId}/edit")
	public String processUpdateForm(@PathVariable("ownerId") int ownerId, Owner owner, @Valid Pet pet,
			BindingResult result, RedirectAttributes redirectAttributes) {

		String petName = pet.getName();

		// checking if the pet name already exists for the owner
		if (StringUtils.hasText(petName)) {
			Pet existingPet = owner.getPet(petName, false);
			if (existingPet != null && !Objects.equals(existingPet.getId(), pet.getId())) {
				result.rejectValue("name", "duplicate", "already exists");
			}
		}

		LocalDate currentDate = LocalDate.now();
		if (pet.getBirthDate() != null && pet.getBirthDate().isAfter(currentDate)) {
			result.rejectValue("birthDate", "typeMismatch.birthDate");
		}

		if (result.hasErrors()) {
			return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
		}

		boolean updated;
		try {
			updated = updatePetDetails(ownerId, pet);
		}
		catch (DataIntegrityViolationException ex) {
			if (!isDuplicatePetNameViolation(ex)) {
				throw ex;
			}
			updated = false;
		}
		if (!updated) {
			result.rejectValue("name", "duplicate", "already exists");
			return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
		}
		redirectAttributes.addFlashAttribute("message", "Pet details has been edited");
		return "redirect:/owners/{ownerId}";
	}

	/**
	 * Adds a new pet to the owner unless another pet of that owner already has the same
	 * name. The owner is re-read under a row lock so that the check and the insert are
	 * atomic with respect to concurrent requests for the same owner; merging the detached
	 * form owner instead would silently unlink pets added in the meantime.
	 * @param ownerId The id of the owner
	 * @param pet The new pet
	 * @return {@code false} if the name is already in use
	 */
	private boolean addPetIfNameAvailable(int ownerId, Pet pet) {
		return Boolean.TRUE.equals(this.transactionTemplate.execute(status -> {
			Owner lockedOwner = findOwnerForUpdate(ownerId);
			if (lockedOwner.getPet(pet.getName(), true) != null) {
				return false;
			}
			lockedOwner.addPet(pet);
			this.owners.saveAndFlush(lockedOwner);
			return true;
		}));
	}

	/**
	 * Updates the pet details if it exists or adds a new pet to the owner, unless another
	 * pet of that owner already has the same name. The owner is re-read under a row lock,
	 * see {@link #addPetIfNameAvailable(int, Pet)}.
	 * @param ownerId The id of the owner
	 * @param pet The pet with updated details
	 * @return {@code false} if the name is already in use by another pet
	 */
	private boolean updatePetDetails(int ownerId, Pet pet) {
		Integer id = pet.getId();
		Assert.state(id != null, "'pet.getId()' must not be null");
		return Boolean.TRUE.equals(this.transactionTemplate.execute(status -> {
			Owner lockedOwner = findOwnerForUpdate(ownerId);
			Pet sameNamePet = lockedOwner.getPet(pet.getName(), false);
			if (sameNamePet != null && !Objects.equals(sameNamePet.getId(), id)) {
				return false;
			}
			Pet existingPet = lockedOwner.getPet(id);
			if (existingPet != null) {
				// Update existing pet's properties
				existingPet.setName(pet.getName());
				existingPet.setBirthDate(pet.getBirthDate());
				existingPet.setType(pet.getType());
			}
			else {
				lockedOwner.addPet(pet);
			}
			this.owners.saveAndFlush(lockedOwner);
			return true;
		}));
	}

	private Owner findOwnerForUpdate(int ownerId) {
		return this.owners.findByIdForUpdate(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
	}

	private boolean isDuplicatePetNameViolation(DataIntegrityViolationException ex) {
		String message = ex.getMessage();
		return message != null && message.toLowerCase().contains("unique_owner_pet_name");
	}

}
