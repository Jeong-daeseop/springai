package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.SkeletonSlotKind;
import com.krdevops.springai.model.thymeleaf.ThymeleafSkeleton;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.krdevops.springai.model.thymeleaf.SkeletonSlotKind.ACTION_BAR;
import static com.krdevops.springai.model.thymeleaf.SkeletonSlotKind.DATA_TABLE;
import static com.krdevops.springai.model.thymeleaf.SkeletonSlotKind.DISPLAY_FIELDS;
import static com.krdevops.springai.model.thymeleaf.SkeletonSlotKind.FORM_FIELDS;
import static com.krdevops.springai.model.thymeleaf.SkeletonSlotKind.SEARCH_FORM;

/**
 * I-4A(단순화): {@link LegacyScreenRole}은 이미 I-2에서 확정돼 있으므로 별도 화면유형 판정기가
 * 필요 없다. 이 서비스는 screenRole → 구조 slot 순서만 결정한다(값 바인딩은 하지 않는다).
 * Component Inventory/Registry 연동과 BOARD·MASTER_DETAIL layoutPattern 세분화는 후속 범위다.
 */
@Service
public class ThymeleafSkeletonPlanner {

    private static final Map<LegacyScreenRole, List<SkeletonSlotKind>> SLOTS_BY_ROLE = buildSlotMap();

    private static Map<LegacyScreenRole, List<SkeletonSlotKind>> buildSlotMap() {
        Map<LegacyScreenRole, List<SkeletonSlotKind>> map = new EnumMap<>(LegacyScreenRole.class);
        map.put(LegacyScreenRole.LIST, List.of(SEARCH_FORM, DATA_TABLE, ACTION_BAR));
        map.put(LegacyScreenRole.FORM, List.of(FORM_FIELDS, ACTION_BAR));
        map.put(LegacyScreenRole.DETAIL, List.of(DISPLAY_FIELDS, ACTION_BAR));
        return map;
    }

    public ThymeleafSkeleton plan(String screenId, LegacyScreenRole screenRole, String pageTitle) {
        return plan(screenId, screenRole, pageTitle, ThymeleafLayoutValidator.DEFAULT_LAYOUT_VIEW);
    }

    public ThymeleafSkeleton plan(
            String screenId, LegacyScreenRole screenRole, String pageTitle, String layoutFragmentRef) {
        List<SkeletonSlotKind> slots = SLOTS_BY_ROLE.get(screenRole);
        if (slots == null) {
            throw new IllegalArgumentException("UNSUPPORTED_SCREEN_ROLE: " + screenRole);
        }
        return new ThymeleafSkeleton(screenId, screenRole, pageTitle, layoutFragmentRef, slots);
    }
}
