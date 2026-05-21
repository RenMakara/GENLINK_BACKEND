package co.istad.makara.account.rest;

import co.istad.makara.account.application.ports.input.service.AccountService;
import co.istad.makara.account.application.dto.create.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final AccountService accountService;


    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public CreateAccountResponse createAccountResponse(@RequestBody CreateAccountRequest request) {
        return accountService.createAccount(request);
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/deposit")
    public CreateDepositResponse createDepositResponse(@RequestBody CreateDepositRequest request) {
        CreateDepositResponse response = accountService.depositAmount(request);
        log.info("Deposit amount : {}", response.amount());
        return response;
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/withdrawal")
    public CreateWithdrawalResponse createWithdrawalReponse(@RequestBody CreateWithdrawalRequest request){
        CreateWithdrawalResponse response = accountService.withdrawalAmount(request);
        log.info("Withdrawal amount : {}", response.amount());
        return response;
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/frozen")
    public CreateFrozenResponse createFrozenResponse(@RequestBody CreateFrozenRequest frozenRequest){
        log.info("Frozen request : {}", frozenRequest);

        return accountService.freezeAccount(frozenRequest);
    }


}
