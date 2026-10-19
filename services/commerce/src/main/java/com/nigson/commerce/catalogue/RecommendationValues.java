package com.nigson.commerce.catalogue;

import java.util.*;
import tools.jackson.databind.JsonNode;

/** Closed catalogue recommendation contracts. */
public final class RecommendationValues {
    private RecommendationValues() {}
    public record Recommendation(UUID relatedProductId,String kind,int priority) {}
    public record Command(UUID relatedProductId,String kind,int priority) {}
    public record Result(UUID productId,List<Recommendation> items,long version) {}
    public record PublicItem(UUID productId,String name,String reason) {}
    public record Knowledge(UUID id,long revision,boolean approved,List<String> marketCodes,String name,String description,JsonNode specifications,String publicUrl,String checksum) {}
    public static List<Command> command(JsonNode node) {
        CatalogueValues.object(node,Set.of("items"),false);
        if(node.size()!=1||!node.get("items").isArray()||node.get("items").size()>100)throw com.nigson.shared.problem.ApiValues.invalid();
        var out=new ArrayList<Command>();var seen=new HashSet<String>();
        for(JsonNode item:node.get("items")) {
            CatalogueValues.object(item,Set.of("relatedProductId","kind","priority"),false);
            if(item.size()!=3)throw com.nigson.shared.problem.ApiValues.invalid();
            UUID related=CatalogueValues.uuid(item,"relatedProductId",false);String kind=CatalogueValues.string(item,"kind",20,false,false);int priority=CatalogueValues.integer(item,"priority",Integer.MIN_VALUE);
            if(!Set.of("RELATED","FEATURED").contains(kind)||priority==Integer.MIN_VALUE||!seen.add(related+":"+kind))throw com.nigson.shared.problem.ApiValues.invalid();
            out.add(new Command(related,kind,priority));
        }
        out.sort(Comparator.comparing(Command::relatedProductId).thenComparing(Command::kind));return List.copyOf(out);
    }
}
