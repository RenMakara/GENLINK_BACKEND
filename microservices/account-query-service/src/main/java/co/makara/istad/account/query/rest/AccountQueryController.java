package co.makara.istad.account.query.rest;

import co.makara.istad.account.query.applicationservice.dto.AccountQueryResponse;
import co.makara.istad.account.query.applicationservice.ports.input.service.AccountQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountQueryController {

    private final AccountQueryService accountQueryService;
    @GetMapping("/{accountId}")
    Mono<AccountQueryResponse> getByAccountId(@PathVariable UUID accountId){
        return accountQueryService.getByAccountId(accountId);
    }
}
