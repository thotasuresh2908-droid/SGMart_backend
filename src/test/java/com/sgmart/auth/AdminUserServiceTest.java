package com.sgmart.auth;

import com.sgmart.auth.AdminUserDtos.AddAdminRequest;
import com.sgmart.auth.AdminUserDtos.UpdateAdminRequest;
import com.sgmart.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {

    private final AdminUserRepository repository = mock(AdminUserRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneOffset.UTC);
    private final AdminUserService service = new AdminUserService(repository, clock);

    private AdminUser admin(long id, String email, AdminRole role, boolean active) {
        AdminUser a = new AdminUser(email, "Name", role);
        a.setActive(active);
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    @Test
    void allowlistedAdminCanSignInAndLoginTimeIsSaved() {
        AdminUser owner = admin(1, "owner.sgmart@gmail.com", AdminRole.OWNER, true);
        when(repository.findByEmailIgnoreCase("Owner.SGMart@gmail.com")).thenReturn(Optional.of(owner));

        assertThat(service.recordSignIn("Owner.SGMart@gmail.com")).contains(owner);
        assertThat(owner.getLastLoginAt()).isEqualTo(Instant.parse("2026-11-12T03:00:00Z"));
    }

    @Test
    void inactiveOrUnknownEmailsCannotSignIn() {
        when(repository.findByEmailIgnoreCase("old@gmail.com"))
                .thenReturn(Optional.of(admin(2, "old@gmail.com", AdminRole.MANAGER, false)));
        when(repository.findByEmailIgnoreCase("stranger@gmail.com")).thenReturn(Optional.empty());

        assertThat(service.recordSignIn("old@gmail.com")).isEmpty();
        assertThat(service.recordSignIn("stranger@gmail.com")).isEmpty();
    }

    @Test
    void lastOwnerCannotBeDemotedOrDeactivated() {
        when(repository.findById(1L)).thenReturn(Optional.of(admin(1, "owner@gmail.com", AdminRole.OWNER, true)));
        when(repository.countByRoleAndActiveTrue(AdminRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> service.update(1L, new UpdateAdminRequest("Owner", AdminRole.MANAGER, true)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("LAST_OWNER");
        assertThatThrownBy(() -> service.update(1L, new UpdateAdminRequest("Owner", AdminRole.OWNER, false)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("LAST_OWNER");
    }

    @Test
    void ownerCanStepDownWhenAnotherOwnerExists() {
        AdminUser owner = admin(1, "owner@gmail.com", AdminRole.OWNER, true);
        when(repository.findById(1L)).thenReturn(Optional.of(owner));
        when(repository.countByRoleAndActiveTrue(AdminRole.OWNER)).thenReturn(2L);

        service.update(1L, new UpdateAdminRequest("Owner", AdminRole.MANAGER, true));
        assertThat(owner.getRole()).isEqualTo(AdminRole.MANAGER);
    }

    @Test
    void duplicateAdminEmailIsRefusedAndNewEmailsAreLowercased() {
        when(repository.findByEmailIgnoreCase("manager@gmail.com"))
                .thenReturn(Optional.of(admin(3, "manager@gmail.com", AdminRole.MANAGER, true)));
        assertThatThrownBy(() -> service.add(new AddAdminRequest("Manager@gmail.com", "M", AdminRole.MANAGER)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("ADMIN_EXISTS");

        when(repository.findByEmailIgnoreCase("new.person@gmail.com")).thenReturn(Optional.empty());
        when(repository.save(any(AdminUser.class))).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.add(new AddAdminRequest(" New.Person@gmail.com ", "New", AdminRole.MANAGER)).email())
                .isEqualTo("new.person@gmail.com");
    }
}
