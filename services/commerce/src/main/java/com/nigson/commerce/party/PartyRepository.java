package com.nigson.commerce.party;
import java.util.*;
import tools.jackson.databind.JsonNode;
import com.nigson.shared.security.ActorAssertion;
public interface PartyRepository {
    void requireCurrent(ActorAssertion.Actor actor,boolean lock);
    PartyValues.Profile profile(UUID party);
    void patchProfile(UUID party,long version,JsonNode patch);
    List<PartyValues.Address> addresses(UUID party);
    List<PartyValues.Organization> organizations(UUID party);
    PartyValues.Address address(UUID party,UUID id);
    void requireOrganization(UUID party,UUID organization,boolean lock);
    void validateCountry(PartyValues.Address address);
    void insertAddress(UUID party,PartyValues.Address address,UUID correlation);
    void patchAddress(UUID party,PartyValues.Address before,PartyValues.Address updated,UUID correlation);
    void deleteAddress(UUID party,PartyValues.Address address);
    UUID provision(PartyValues.VerifiedIdentity identity);
}
