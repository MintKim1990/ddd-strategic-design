package kitchenpos.application.exhibit.menu;

import kitchenpos.exhibit.menu.infra.MenuPurgomalum;

import java.util.Arrays;
import java.util.List;

public class MenuFakePurgomalumClient implements MenuPurgomalum {
    private static final List<String> profanities;

    static {
        profanities = Arrays.asList("비속어", "욕설");
    }

    @Override
    public boolean containsProfanity(final String text) {
        return profanities.stream()
                .anyMatch(profanity -> text.contains(profanity));
    }
}
