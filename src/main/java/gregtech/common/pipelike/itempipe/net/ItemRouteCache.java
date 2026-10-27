package gregtech.common.pipelike.itempipe.net;

import java.util.List;

final class ItemRouteCache {

    final List<ItemRoutePath> routes;
    final long createdAt;

    ItemRouteCache(List<ItemRoutePath> routes, long createdAt) {
        this.routes = routes;
        this.createdAt = createdAt;
    }
}
